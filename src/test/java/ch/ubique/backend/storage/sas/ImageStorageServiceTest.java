package ch.ubique.backend.storage.sas;

import ch.ubique.backend.image.blurhash.BlurHashClient;
import ch.ubique.backend.storage.clients.AzureBlobStorageClient;
import ch.ubique.backend.storage.images.ImageStorageService;
import ch.ubique.backend.storage.images.ImageVariant;
import ch.ubique.backend.storage.output.ByteOutput;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImageStorageServiceTest {
    private static final Function<Integer, Function<ImageVariant, String>> GENERATE_GENERATE_PATH =
            imageId -> variant -> String.format("sasImage/%d-%s.jpeg", imageId, variant.key);
    private static final Function<Integer, Function<ImageVariant, String>> GENERATE_GENERATE_PATH_PNG =
            imageId -> variant  -> String.format("sasImage/%d-%s.png", imageId, variant.key);

    private final AzureBlobStorageClient blobStorageService = mock(AzureBlobStorageClient.class);
    private final Duration sasExpiry = Duration.of(3, ChronoUnit.DAYS);
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final BlurHashClient blurHashClient = new BlurHashClient(10);

    private final ImageStorageService imageStorageService =
            new ImageStorageService(blobStorageService, sasExpiry, executorService, blurHashClient);

    @Test
    void testImageUpload() throws IOException, ExecutionException, InterruptedException {
        final var imageOutput = new ByteArrayOutputStream();
        final var image = ImageIO.read(new File("src/test/resources/data/ubique_main_image.jpg"));
        ImageIO.write(image, "jpeg", imageOutput);
        final var exampleImage = imageOutput.toByteArray();

        final var blurHashRepresentation =
                imageStorageService.processImage(
                        new ByteArrayInputStream(exampleImage), GENERATE_GENERATE_PATH.apply(0), MediaType.IMAGE_JPEG);
        assertEquals("UXJRXJ?GI:s;~qoLWExttSRjRPbHM_WCoeof", blurHashRepresentation.blurHash());
        assertEquals(1.77, blurHashRepresentation.aspectRatio(), 0.05);
        verify(blobStorageService, atLeastOnce())
                .write(new ByteOutput(exampleImage, "image/jpeg"), "sasImage/0-original.jpeg");
        verify(blobStorageService, atLeastOnce()).write(any(), eq("sasImage/0-hires.jpeg"));
        verify(blobStorageService, atLeastOnce()).write(any(), eq("sasImage/0-thumbnail.jpeg"));
    }
    @Test
    void testPngImageUpload() throws IOException, ExecutionException, InterruptedException {
        final var imageOutput = new ByteArrayOutputStream();
        final var image = ImageIO.read(new File("src/test/resources/data/png.png"));
        ImageIO.write(image, "png", imageOutput);
        final var exampleImage = imageOutput.toByteArray();

        final var blurHashRepresentation =
                imageStorageService.processImage(
                        new ByteArrayInputStream(exampleImage), GENERATE_GENERATE_PATH_PNG.apply(1),
                        MediaType.IMAGE_PNG);
        verify(blobStorageService, atLeastOnce())
                .write(new ByteOutput(exampleImage, "image/png"), "sasImage/1-original.png");
        verify(blobStorageService, atLeastOnce()).write(any(), eq("sasImage/1-hires.png"));
        verify(blobStorageService, atLeastOnce()).write(any(), eq("sasImage/1-thumbnail.png"));
    }

    @Test
    void testImageSas() {
        imageStorageService.getSignedUrl(GENERATE_GENERATE_PATH.apply(0).apply(ImageVariant.HIRES));
        verify(blobStorageService, atLeastOnce()).getSignedUrl(eq("sasImage/0-hires.jpeg"), any());
    }
}
