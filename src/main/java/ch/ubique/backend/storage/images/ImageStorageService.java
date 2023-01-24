package ch.ubique.backend.storage.images;

import ch.ubique.backend.image.blurhash.BlurHashClient;
import ch.ubique.backend.image.blurhash.BlurHashRepresentation;
import ch.ubique.backend.image.util.ExifTransform;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;
import com.drew.imaging.ImageProcessingException;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.imgscalr.Scalr;
import org.springframework.http.MediaType;

/**
 * Saves images in multiple, resized variants and retrieve singed access urls to the uploaded
 * resources using the provided {@link StorageClient} .
 */
@SuppressWarnings("unused")
public class ImageStorageService {
    private final StorageClient storageClient;
    private final Duration sasExpiry;
    private final BlurHashClient blurHashClient;

    public ImageStorageService(
            final StorageClient storageClient,
            final Duration sasExpiry,
            final BlurHashClient blurHashClient) {
        this.storageClient = storageClient;
        this.sasExpiry = sasExpiry;
        this.blurHashClient = blurHashClient;
    }

    /** Construct with default sasExpiry of 7 days. Useful if sas is not used. */
    public ImageStorageService(
            final StorageClient storageClient, final BlurHashClient blurHashClient) {
        this.storageClient = storageClient;
        this.sasExpiry = Duration.ofDays(7);
        this.blurHashClient = blurHashClient;
    }

    @NotNull
    public BlurHashRepresentation processImage(
            final InputStream imageInputStream,
            final Function<ImageVariant, String> generatePath,
            final MediaType mediaType)
            throws IOException, ImageProcessingException {
        final var imageBytes = imageInputStream.readAllBytes();
        // InputStream::readAllBytes does not close the imageInputStream
        imageInputStream.close();
        return processImage(imageBytes, generatePath, mediaType);
    }

    @NotNull
    public BlurHashRepresentation processImage(
            final byte[] imageBytes,
            final Function<ImageVariant, String> generatePath,
            final MediaType mediaType)
            throws IOException, ImageProcessingException {
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
        // ImageIO::read does not close the stream
        imageStream.close();

        final var hires = Scalr.resize(bufferedImage, 1200);
        // Flush clears native references so the image can be GC-ed. See tip of Scalr::resize
        bufferedImage.flush();
        final var hiresOutput = new ByteArrayOutputStream();
        ImageIO.write(hires, mediaType.getSubtype(), hiresOutput);
        storageClient.write(
                new ByteOutput(hiresOutput.toByteArray(), mediaType.toString()),
                generatePath.apply(ImageVariant.HIRES));
        // ByteArrayOutputStream::toByteArray does not close the stream
        hiresOutput.close();

        final var thumbnail = Scalr.resize(hires, 320);
        // Flush clears native references so the image can be GC-ed. See tip of Scalr::resize
        hires.flush();
        final var thumbnailOutput = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, mediaType.getSubtype(), thumbnailOutput);
        storageClient.write(
                new ByteOutput(thumbnailOutput.toByteArray(), mediaType.toString()),
                generatePath.apply(ImageVariant.THUMBNAIL));
        // ByteArrayOutputStream::toByteArray does not close the stream
        thumbnailOutput.close();

        final var blurHashRepresentation = blurHashClient.encodeToBlurHashRepresentationNoResize(thumbnail);
        // Flush clears native references so the image can be GC-ed. See tip of Scalr::resize
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
