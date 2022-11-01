package ch.ubique.backend.storage.images;

import ch.ubique.backend.image.blurhash.BlurHashClient;
import ch.ubique.backend.image.blurhash.BlurHashRepresentation;
import ch.ubique.backend.image.util.ExifTransform;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;

import com.drew.imaging.ImageProcessingException;

import org.imgscalr.Scalr;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import javax.imageio.ImageIO;
import org.springframework.http.MediaType;

/**
 * Saves images in multiple, resized variants and retrieve singed access urls to the uploaded
 * resources using the provided {@link StorageClient} .
 */
public class ImageStorageService {

    private final StorageClient storageClient;
    private final Duration sasExpiry;
    private final ExecutorService highMemoryExecutor;
    private final BlurHashClient blurHashClient;

    public ImageStorageService(
            final StorageClient storageClient,
            final Duration sasExpiry,
            final ExecutorService highMemoryExecutor,
            final BlurHashClient blurHashClient) {
        this.storageClient = storageClient;
        this.sasExpiry = sasExpiry;
        this.highMemoryExecutor = highMemoryExecutor;
        this.blurHashClient = blurHashClient;
    }

    /** Construct with default sasExpiry of 7 days. Useful if sas is not used. */
    public ImageStorageService(
            final StorageClient storageClient,
            final ExecutorService highMemoryExecutor,
            final BlurHashClient blurHashClient) {
        this.storageClient = storageClient;
        this.sasExpiry = Duration.ofDays(7);
        this.highMemoryExecutor = highMemoryExecutor;
        this.blurHashClient = blurHashClient;
    }

    /**
     *
     * @param imageInputStream
     * @param generatePath
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     * @deprecated (since 0.7.4, only Image JPEG was supported, use processImagge with mediatype)
     */
    @Deprecated(since = "0.7.4")
    public BlurHashRepresentation processImage(
            final InputStream imageInputStream, final Function<ImageVariant, String> generatePath)
            throws ExecutionException, InterruptedException {
        return processImage(imageInputStream,
                generatePath, MediaType.IMAGE_JPEG);
    }
    public BlurHashRepresentation processImage(
            final InputStream imageInputStream, final Function<ImageVariant, String> generatePath, MediaType mediaType)
            throws ExecutionException, InterruptedException {
        return highMemoryExecutor
                .submit(() -> processImageInternal(imageInputStream, generatePath, mediaType))
                .get();
    }

    @NotNull
    private BlurHashRepresentation processImageInternal(
            final InputStream imageInputStream, final Function<ImageVariant, String> generatePath, MediaType mediaType)
            throws IOException, ImageProcessingException {
        final byte[] imageBytes = imageInputStream.readAllBytes();
        // InputStream::readAllBytes does not close the imageInputStream
        imageInputStream.close();
        storageClient.write(
                new ByteOutput(imageBytes, mediaType.toString()),
                generatePath.apply(ImageVariant.ORIGINAL));

        final var imageInfo = ExifTransform.readImageInformation(imageBytes);
        final var imageStream = new ByteArrayInputStream(imageBytes);
        final var bufferedImage =
                ExifTransform.transformImage(
                        ImageIO.read(imageStream),
                        ExifTransform.getExifTransformation(imageInfo),
                        imageInfo);
        // ImageIO::read does nto close the imageStream
        imageStream.close();

        final var hires = Scalr.resize(bufferedImage, 1200);
        bufferedImage.flush();
        // Flush clears native references so the image can be GC-ed. See tip of Scalr::resize
        final var hiresOutput = new ByteArrayOutputStream();
        ImageIO.write(hires, mediaType.getSubtype(), hiresOutput);
        storageClient.write(
                new ByteOutput(hiresOutput.toByteArray(), mediaType.toString()),
                generatePath.apply(ImageVariant.HIRES));

        final var thumbnail = Scalr.resize(hires, 320);
        hires.flush();
        // Flush clears native references so the image can be GC-ed. See tip of Scalr::resize
        final var thumbnailOutput = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, mediaType.getSubtype(), thumbnailOutput);

        storageClient.write(
                new ByteOutput(thumbnailOutput.toByteArray(), mediaType.toString()),
                generatePath.apply(ImageVariant.THUMBNAIL));

        final var blurHashRepresentation =
                blurHashClient.encodeToBlurHash(thumbnail, BlurHashClient.FALLBACK_GREY_BLUR_HASH);
        // Flush clears native references so the image can be GC-ed. See tip of
        // Scalr::resize
        thumbnail.flush();
        return blurHashRepresentation;
    }

    public void deleteImageVariants(final Function<ImageVariant, String> generatePath)
            throws IOException {
        for (final var variant : ImageVariant.values()) {
            deleteImageVariant(generatePath.apply(variant));
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteImageVariant(final String path) throws IOException {
        return storageClient.delete(path);
    }

    public byte[] downloadBlobAsBytes(final String path) throws IOException {
        try (final var inputStream = storageClient.read(path)) {
            return inputStream.readAllBytes();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String getSignedUrl(final String path) {
        return storageClient.getSignedUrl(
                path, OffsetDateTime.now().plusNanos(sasExpiry.toNanos()));
    }

    public String getUrl(final String path) {
        return storageClient.getUrl(path);
    }
}
