package ch.ubique.backend.storage.sas;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ch.ubique.backend.image.blurhash.BlurHashClient;
import ch.ubique.backend.storage.clients.FileSystemStorageClient;
import ch.ubique.backend.storage.images.ImageStorageService;
import ch.ubique.backend.storage.images.ImageVariant;
import ch.ubique.backend.storage.output.InputStreamOutput;

import com.drew.imaging.ImageProcessingException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.*;
import java.time.Duration;
import java.util.function.Function;

import javax.imageio.ImageIO;

class ImageStorageServiceTest {
    private static final Function<Integer, Function<ImageVariant, String>> GENERATE_GENERATE_PATH =
            imageId -> variant -> String.format("sasImage/%d-%s.jpeg", imageId, variant.key);
    private static final Function<Integer, Function<ImageVariant, String>>
            GENERATE_GENERATE_PATH_PNG =
                    imageId -> variant -> String.format("sasImage/%d-%s.png", imageId, variant.key);

    private final FileSystemStorageClient storageClient = mock(FileSystemStorageClient.class);
    private final Duration sasExpiry = Duration.ofDays(3);
    private final BlurHashClient blurHashClient = new BlurHashClient(10, "ub-backend-jvm-lib");

    private final ImageStorageService imageStorageService =
            new ImageStorageService(storageClient, sasExpiry, blurHashClient);

    @Test
    void testImageUpload() throws IOException, ImageProcessingException {
        final var imageOutput = new ByteArrayOutputStream();
        final var image = ImageIO.read(new File("src/test/resources/data/ubique_main_image.jpg"));
        ImageIO.write(image, "jpeg", imageOutput);
        final var exampleImage = imageOutput.toByteArray();
        final var exampleImageInputStream = new ByteArrayInputStream(exampleImage);

        doReturn(new ByteArrayInputStream(exampleImage))
                .when(storageClient)
                .read("sasImage/1-original.jpeg");
        final var blurHashRepresentation =
                imageStorageService.processImage(
                        exampleImageInputStream,
                        GENERATE_GENERATE_PATH.apply(1),
                        MediaType.IMAGE_JPEG);
        assertEquals("UWJa[-?GIps:~qoKWExttSRjRPbHMxWCoeof", blurHashRepresentation.blurHash());
        assertEquals(1.77, blurHashRepresentation.aspectRatio(), 0.05);
        verify(storageClient, atLeastOnce())
                .write(
                        new InputStreamOutput(exampleImageInputStream, MediaType.IMAGE_JPEG_VALUE),
                        "sasImage/1-original.jpeg");
        verify(storageClient, atLeastOnce()).write(any(), eq("sasImage/1-hires.jpeg"));
        verify(storageClient, atLeastOnce()).write(any(), eq("sasImage/1-thumbnail.jpeg"));
    }

    @Test
    void testImageUploadTwoSteps() throws IOException, ImageProcessingException {
        final var imageOutput = new ByteArrayOutputStream();
        final var image = ImageIO.read(new File("src/test/resources/data/ubique_main_image.jpg"));
        ImageIO.write(image, "jpeg", imageOutput);
        final var exampleImage = imageOutput.toByteArray();
        final var exampleImageInputStream = new ByteArrayInputStream(exampleImage);
        imageStorageService.uploadOriginalImage(
                exampleImageInputStream, GENERATE_GENERATE_PATH.apply(1), MediaType.IMAGE_JPEG);
        verify(storageClient, atLeastOnce())
                .write(
                        new InputStreamOutput(exampleImageInputStream, MediaType.IMAGE_JPEG_VALUE),
                        "sasImage/1-original.jpeg");

        doReturn(new ByteArrayInputStream(exampleImage))
                .when(storageClient)
                .read("sasImage/1-original.jpeg");
        final var blurHashRepresentation =
                imageStorageService.processImageFromOriginal(
                        GENERATE_GENERATE_PATH.apply(1), MediaType.IMAGE_JPEG);
        assertEquals("UWJa[-?GIps:~qoKWExttSRjRPbHMxWCoeof", blurHashRepresentation.blurHash());
        assertEquals(1.77, blurHashRepresentation.aspectRatio(), 0.05);
        verify(storageClient, atLeastOnce()).write(any(), eq("sasImage/1-hires.jpeg"));
        verify(storageClient, atLeastOnce()).write(any(), eq("sasImage/1-thumbnail.jpeg"));
    }

    @Test
    void testPngImageUpload() throws IOException {
        final var imageOutput = new ByteArrayOutputStream();
        final var image = ImageIO.read(new File("src/test/resources/data/png.png"));
        ImageIO.write(image, "png", imageOutput);
        final var exampleImage = imageOutput.toByteArray();
        final var exampleImageInputStream = new ByteArrayInputStream(exampleImage);

        doReturn(new ByteArrayInputStream(exampleImage))
                .when(storageClient)
                .read("sasImage/1-original.png");
        assertDoesNotThrow(
                () ->
                        imageStorageService.processImage(
                                exampleImageInputStream,
                                GENERATE_GENERATE_PATH_PNG.apply(1),
                                MediaType.IMAGE_PNG));
        verify(storageClient, atLeastOnce())
                .write(
                        new InputStreamOutput(exampleImageInputStream, MediaType.IMAGE_PNG_VALUE),
                        "sasImage/1-original.png");
        verify(storageClient, atLeastOnce()).write(any(), eq("sasImage/1-hires.png"));
        verify(storageClient, atLeastOnce()).write(any(), eq("sasImage/1-thumbnail.png"));
    }

    @Test
    void testImageSas() throws IOException {
        imageStorageService.getSignedUrl(GENERATE_GENERATE_PATH.apply(0).apply(ImageVariant.HIRES));
        verify(storageClient, atLeastOnce()).getSignedUrl(eq("sasImage/0-hires.jpeg"), any());
    }
}
