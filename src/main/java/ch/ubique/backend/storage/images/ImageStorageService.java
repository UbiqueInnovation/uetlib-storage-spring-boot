package ch.ubique.backend.storage.images;

import ch.ubique.backend.image.blurhash.BlurHashClient;
import ch.ubique.backend.image.blurhash.BlurHashRepresentation;
import ch.ubique.backend.image.util.ImageUtils;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.InputStreamOutput;

import com.drew.imaging.ImageProcessingException;

import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Function;

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
        uploadOriginalImage(imageInputStream, generatePath, mediaType);
        return processImageFromOriginal(generatePath, mediaType);
    }

    public void uploadOriginalImage(
            final InputStream imageInputStream,
            final Function<ImageVariant, String> generatePath,
            final MediaType mediaType)
            throws IOException {
        storageClient.write(
                new InputStreamOutput(imageInputStream, mediaType.toString()),
                generatePath.apply(ImageVariant.ORIGINAL));
    }

    @NotNull
    public BlurHashRepresentation processImageFromOriginal(
            final Function<ImageVariant, String> generatePath, final MediaType mediaType)
            throws IOException, ImageProcessingException {
        final var originalImageInputStream =
                storageClient.read(generatePath.apply(ImageVariant.ORIGINAL));
        final var originalImageBytes = originalImageInputStream.readAllBytes();
        // InputStream::readAllBytes does not close the imageInputStream
        originalImageInputStream.close();

        final var hires =
                ImageUtils.scaleAndFlush(ImageUtils.imageFromBytes(originalImageBytes), 1200);
        storageClient.write(
                new ByteOutput(ImageUtils.imageToBytes(hires, mediaType), mediaType.toString()),
                generatePath.apply(ImageVariant.HIRES));

        final var thumbnail = ImageUtils.scaleAndFlush(hires, 320);

        storageClient.write(
                new ByteOutput(ImageUtils.imageToBytes(thumbnail, mediaType), mediaType.toString()),
                generatePath.apply(ImageVariant.THUMBNAIL));

        final var blurHashRepresentation =
                blurHashClient.encodeToBlurHashRepresentationNoResize(thumbnail);
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
