package ch.ubique.backend.storage.annotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.config.implementation.EnableStoragesImplementationTestConfig;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.GZipOutput;
import ch.ubique.backend.storage.output.JsonOutput;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@SpringBootTest(classes = EnableStoragesImplementationTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class EnableStoragesImplementationTest {

    @SpyBean private ObjectMapper objectMapper;
    @SpyBean private StorageClient storageClient;
    @SpyBean private AnnotatedTestStorage testStorage;
    @SpyBean private TestStorageReferenceImplementation referenceImplementation;

    @Value("classpath:storage/files/2.png")
    private Resource png2;

    @BeforeEach
    void beforeEach() throws IOException {
        MockitoAnnotations.openMocks(this);
        storageClient.delete("", true);
        storageClient.write(
                new JsonOutput<>(new TestUser(17, "Professor", "Jaffe Presley")),
                "storage/files/17.json");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "storage/files/2.png");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "storage/files/61.png");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "storage/files/5/54.png");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "storage/files/5/42.png");
    }

    @Test
    void testInputStorage(final CapturedOutput capturedOutput) throws IOException {
        assertNotNull(testStorage);
        assertNotNull(referenceImplementation);

        final var testUser = testStorage.getJson(17);
        assertEquals(new TestUser(17, "Professor", "Jaffe Presley"), testUser);
        verify(storageClient).read("storage/files/17.json", true);
        // testStorage.getJson has log level to DEBUG, but the test logger is set to INFO, so
        // correctly no log from AnnotatedTestStorage
        assertThat(ArrayUtils.lastElement(capturedOutput.getOut().split("\n")))
                .doesNotContain("AnnotatedTestStorage");

        final var imageCompressed = testStorage.getImageCompressed(2);
        assertArrayEquals(png2.getContentAsByteArray(), imageCompressed);
        verify(storageClient).read("storage/files/2.png", false);
        // testStorage.getImageCompressed has log level null, so correctly no log from
        // AnnotatedTestStorage
        assertThat(ArrayUtils.lastElement(capturedOutput.getOut().split("\n")))
                .doesNotContain("AnnotatedTestStorage");

        final var image = testStorage.getImage(2);
        assertArrayEquals(png2.getContentAsByteArray(), image);
        verify(storageClient).read("storage/files/2.png", true);
        final var outputGetImage = capturedOutput.getOut().split("\n");
        final var thirdLastGetImage = outputGetImage[outputGetImage.length - 3];
        final var secondLastGetImage = outputGetImage[outputGetImage.length - 2];
        final var lastGetImage = ArrayUtils.lastElement(outputGetImage);
        assertThat(thirdLastGetImage)
                .containsSequence("INFO")
                .containsSequence("AnnotatedTestStorage")
                .endsWith(
                        "reading content of type image/png from path storage/files/2.png with"
                                + " doUncompressIfNeeded=true");
        assertThat(secondLastGetImage)
                .containsSequence("INFO")
                .containsSequence("InputStreamUtils")
                .endsWith("Could not find compression algorithm. Returning stream as-is");
        assertThat(lastGetImage)
                .containsSequence("INFO")
                .containsSequence("AnnotatedTestStorage")
                .endsWith("by reading the input stream to a byte[]");
    }

    @Test
    void testOutputStorage(final CapturedOutput capturedOutput) throws IOException {
        assertNotNull(testStorage);
        assertNotNull(referenceImplementation);

        final var testUser = new TestUser(32, "Student", "Seong-Hyeon Lebogang");
        testStorage.saveJson(testUser, "32", null);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(testUser, objectMapper)),
                        "storage/files/32.json",
                        null);
        assertEquals(
                testUser,
                objectMapper.readValue(
                        storageClient.read("storage/files/32.json", true), TestUser.class));
        // testStorage.saveJson has log level TRACE, but the test logger is set to INFO, so
        // correctly no log from AnnotatedTestStorage
        assertThat(ArrayUtils.lastElement(capturedOutput.getOut().split("\n")))
                .doesNotContain("AnnotatedTestStorage");

        final var outputMetaData =
                new OutputMetaData(Duration.ofSeconds(48654), Duration.ofMillis(87623))
                        .backoff(Duration.ofHours(2))
                        .nextRefresh(Instant.now().plusMillis(629235637873L));
        testStorage.saveJson(testUser, "32", outputMetaData);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(testUser, objectMapper)),
                        "storage/files/32.json",
                        outputMetaData);
        assertEquals(
                testUser,
                objectMapper.readValue(
                        storageClient.read("storage/files/32.json", true), TestUser.class));

        testStorage.saveImage(png2.getContentAsByteArray(), "8");
        verify(storageClient)
                .write(
                        new GZipOutput(
                                new ByteOutput(png2.getContentAsByteArray(), IMAGE_PNG_VALUE)),
                        "storage/files/8.png",
                        null);
        assertThat(ArrayUtils.lastElement(capturedOutput.getOut().split("\n")))
                .containsSequence("WARN")
                .containsSequence("AnnotatedTestStorage")
                .endsWith(
                        "writing content of type image/png to path storage/files/8.png with size -1"
                                + " and metadata null");
    }

    @Test
    void testDeleteStorage(final CapturedOutput capturedOutput) throws IOException {
        final var deleted62 = testStorage.deleteFile("62");
        verify(storageClient).delete("storage/files/62.png", false);
        assertFalse(deleted62);
        assertEquals(
                Set.of(
                        "storage/files/2.png",
                        "storage/files/5/",
                        "storage/files/17.json",
                        "storage/files/61.png"),
                storageClient.list("storage/files/"));

        final var deleted61 = testStorage.deleteFile("61");
        verify(storageClient).delete("storage/files/61.png", false);
        assertTrue(deleted61);
        assertFalse(
                storageClient
                        .list("storage/files/")
                        .containsAll(
                                Set.of(
                                        "storage/files/2.png",
                                        "storage/files/17.json",
                                        "storage/files/61.png")));
        assertEquals(
                Set.of("storage/files/2.png", "storage/files/5/", "storage/files/17.json"),
                storageClient.list("storage/files/"));

        assertEquals(
                storageClient.list("storage/files/5/"),
                Set.of("storage/files/5/54.png", "storage/files/5/42.png"));
        final var deletedFolder4 = testStorage.deleteFolder(4);
        verify(storageClient).delete("storage/files/4", true);
        assertFalse(deletedFolder4);
        assertEquals(
                Set.of("storage/files/5/54.png", "storage/files/5/42.png"),
                storageClient.list("storage/files/5/"));

        final var deletedFolder5 = testStorage.deleteFolder(5);
        verify(storageClient).delete("storage/files/5", true);
        assertTrue(deletedFolder5);
        assertThrows(NoSuchFileException.class, () -> storageClient.list("storage/files/5/"));
        assertFalse(storageClient.list("storage/files/").contains("storage/files/5/"));
        assertThat(ArrayUtils.lastElement(capturedOutput.getOut().split("\n")))
                .containsSequence("ERROR")
                .containsSequence("AnnotatedTestStorage")
                .endsWith("deleting content from path storage/files/5 with recursive=true");
    }

    @Test
    void testListStorage(final CapturedOutput capturedOutput) throws IOException {
        assertEquals(
                Set.of(
                        "storage/files/2.png",
                        "storage/files/5/",
                        "storage/files/17.json",
                        "storage/files/61.png"),
                testStorage.listFiles("files"));
        verify(storageClient).list("storage/files/", false);

        assertEquals(
                Set.of(
                        "storage/files/2.png",
                        "storage/files/5/54.png",
                        "storage/files/5/42.png",
                        "storage/files/17.json",
                        "storage/files/61.png"),
                testStorage.listFilesRecursive());
        verify(storageClient).list("storage/files/", true);
        assertThat(ArrayUtils.lastElement(capturedOutput.getOut().split("\n")))
                .containsSequence("INFO")
                .containsSequence("AnnotatedTestStorage")
                .endsWith("listing files from path storage/files/ with recursive=true");
    }
}
