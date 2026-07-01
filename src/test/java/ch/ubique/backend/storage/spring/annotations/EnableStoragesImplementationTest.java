package ch.ubique.backend.storage.spring.annotations;

import static ch.ubique.backend.core.utils.ArrayUtils.lastElement;
import static ch.ubique.backend.test.assertion.AssertionUtils.assertThrowsWithMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCollection;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.GZipOutput;
import ch.ubique.backend.storage.output.JsonOutput;
import ch.ubique.backend.storage.spring.config.EnableStoragesTestConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@ActiveProfiles("test")
@SpringBootTest(classes = EnableStoragesTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class EnableStoragesImplementationTest {

    @Autowired private ApplicationContext applicationContext;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private AnnotatedTestStorage annotatedTestStorage;

    @MockitoBean private TestStorageReferenceImplementation referenceImplementation;

    @MockitoSpyBean private StorageClient storageClient;

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
        assertNotNull(annotatedTestStorage);
        assertNotNull(referenceImplementation);

        final var testUser = annotatedTestStorage.getJson(17);
        assertEquals(new TestUser(17, "Professor", "Jaffe Presley"), testUser);
        verify(storageClient).read("storage/files/17.json", true);
        // testStorage.getJson has log level to DEBUG, but the test logger is set to INFO, so
        // correctly no log from AnnotatedTestStorage
        assertThat(lastElement(capturedOutput.getOut().split("\n")))
                .doesNotContain("AnnotatedTestStorage");

        final var imageCompressed = annotatedTestStorage.getImageCompressed(2);
        assertArrayEquals(png2.getContentAsByteArray(), imageCompressed);
        verify(storageClient).read("storage/files/2.png", false);
        // testStorage.getImageCompressed has log level null, so correctly no log from
        // AnnotatedTestStorage
        assertThat(lastElement(capturedOutput.getOut().split("\n")))
                .doesNotContain("AnnotatedTestStorage");

        final var image = annotatedTestStorage.getImage(2);
        assertArrayEquals(png2.getContentAsByteArray(), image);
        verify(storageClient).read("storage/files/2.png", true);
        final var outputGetImage = capturedOutput.getOut().split("\n");
        final var secondLastGetImage = outputGetImage[outputGetImage.length - 2];
        final var lastGetImage = lastElement(outputGetImage);
        assertThat(secondLastGetImage)
                .containsSequence("INFO")
                .containsSequence("AnnotatedTestStorage")
                .endsWith(
                        "reading content of type image/png from path storage/files/2.png with"
                                + " doUncompressIfNeeded=true");
        assertThat(lastGetImage)
                .containsSequence("INFO")
                .containsSequence("AnnotatedTestStorage")
                .endsWith("by reading the input stream to a byte[]");
    }

    @Test
    void testOutputStorage(final CapturedOutput capturedOutput) throws IOException {
        assertNotNull(annotatedTestStorage);
        assertNotNull(referenceImplementation);

        final var testUser = new TestUser(32, "Student", "Seong-Hyeon Lebogang");

        annotatedTestStorage.saveJson(testUser, "32", null);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(testUser, objectMapper)),
                        "storage/files/32.json.gz",
                        null);
        assertEquals(
                testUser,
                objectMapper.readValue(
                        storageClient.read("storage/files/32.json.gz", true), TestUser.class));

        annotatedTestStorage.saveMap(Map.of(32, testUser), "32", null);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(Map.of(32, testUser), objectMapper)),
                        "storage/files/maps/32.json",
                        null);
        final var testUserMap =
                Map.of(
                        32,
                        ofEntries(
                                entry("userId", 32),
                                entry("title", "Student"),
                                entry("name", "Seong-Hyeon Lebogang")));
        final var actualTestUserMap =
                objectMapper.readValue(
                        storageClient.read("storage/files/maps/32.json", true), new TypeReference<Map<Integer, Object>>() {});
        assertEquals(testUserMap, actualTestUserMap);

        Map.of(
                        32,
                        ofEntries(
                                entry("userId", 32),
                                entry("title", "Student"),
                                entry("name", "Seong-Hyeon Lebogang")))
                .equals(
                        objectMapper.readValue(
                                storageClient.read("storage/files/maps/32.json", true), Map.class));

        annotatedTestStorage.saveJsonOutput(new JsonOutput<>(testUser, objectMapper), "32", null);
        verify(storageClient)
                .write(new JsonOutput<>(testUser, objectMapper), "storage/files/32.json", null);
        assertEquals(
                testUser,
                objectMapper.readValue(
                        storageClient.read("storage/files/32.json", true), TestUser.class));

        // testStorage.saveJson has log level TRACE, but the test logger is set to INFO, so
        // correctly no log from AnnotatedTestStorage
        assertThat(lastElement(capturedOutput.getOut().split("\n")))
                .doesNotContain("AnnotatedTestStorage");

        final var outputMetaData =
                new OutputMetaData(Duration.ofSeconds(48654), Duration.ofMillis(87623))
                        .backoff(Duration.ofHours(2))
                        .nextRefresh(Instant.now().plusMillis(629235637873L));
        annotatedTestStorage.saveJson(testUser, "32", outputMetaData);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(testUser, objectMapper)),
                        "storage/files/32.json.gz",
                        outputMetaData);
        assertEquals(
                testUser,
                objectMapper.readValue(
                        storageClient.read("storage/files/32.json.gz", true), TestUser.class));

        annotatedTestStorage.saveImage(png2.getContentAsByteArray(), "8");
        verify(storageClient)
                .write(
                        new GZipOutput(
                                new ByteOutput(png2.getContentAsByteArray(), IMAGE_PNG_VALUE)),
                        "storage/files/8.png",
                        null);

        assertThat(lastElement(capturedOutput.getOut().split("\n")))
                .containsSequence("WARN")
                .containsSequence("AnnotatedTestStorage")
                .endsWith(
                        "writing content of type image/png to path storage/files/8.png with size -1"
                                + " and metadata null");

        annotatedTestStorage.saveImageByteOutput(
                new ByteOutput(png2.getContentAsByteArray(), IMAGE_PNG_VALUE), "8");
        verify(storageClient)
                .write(
                        new GZipOutput(
                                new ByteOutput(png2.getContentAsByteArray(), IMAGE_PNG_VALUE)),
                        "storage/files/8.png",
                        null);

        assertThat(lastElement(capturedOutput.getOut().split("\n")))
                .containsSequence("WARN")
                .containsSequence("AnnotatedTestStorage")
                .endsWith(
                        "writing content of type image/png to path storage/files/output-8.png with size -1"
                                + " and metadata null");
    }

    @Test
    void testDeleteStorage(final CapturedOutput capturedOutput) throws IOException {
        final var deleted62 = annotatedTestStorage.deleteFile("62");
        verify(storageClient).delete("storage/files/62.png", false);
        assertFalse(deleted62);
        assertEquals(
                Set.of(
                        "storage/files/2.png",
                        "storage/files/5/",
                        "storage/files/17.json",
                        "storage/files/61.png"),
                storageClient.list("storage/files/"));

        final var deleted61 = annotatedTestStorage.deleteFile("61");
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
        final var deletedFolder4 = annotatedTestStorage.deleteFolder(4);
        verify(storageClient).delete("storage/files/4", true);
        assertFalse(deletedFolder4);
        assertEquals(
                Set.of("storage/files/5/54.png", "storage/files/5/42.png"),
                storageClient.list("storage/files/5/"));

        final var deletedFolder5 = annotatedTestStorage.deleteFolder(5);
        verify(storageClient).delete("storage/files/5", true);
        assertTrue(deletedFolder5);
        assertThatCollection(storageClient.list("storage/files/5/")).isEmpty();
        assertFalse(storageClient.list("storage/files/").contains("storage/files/5/"));
        assertThat(lastElement(capturedOutput.getOut().split("\n")))
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
                annotatedTestStorage.listFiles("files"));
        verify(storageClient).list("storage/files/", false);

        assertEquals(
                Set.of(
                        "storage/files/2.png",
                        "storage/files/5/54.png",
                        "storage/files/5/42.png",
                        "storage/files/17.json",
                        "storage/files/61.png"),
                annotatedTestStorage.listFilesRecursive());
        verify(storageClient).list("storage/files/", true);
        assertThat(lastElement(capturedOutput.getOut().split("\n")))
                .containsSequence("INFO")
                .containsSequence("AnnotatedTestStorage")
                .endsWith("listing files from path storage/files/ with recursive=true");
    }

    @Test
    void testExistsStorage() throws IOException {
        assertTrue(annotatedTestStorage.existsFile("2"));
        verify(storageClient).exists("storage/files/2.png");

        assertFalse(annotatedTestStorage.existsFile("5"));
        verify(storageClient).exists("storage/files/5.png");

        assertTrue(annotatedTestStorage.existsPathPrefix(5));
        verify(storageClient).exists("storage/files/5");

        assertTrue(annotatedTestStorage.existsPathPrefix(2));
        verify(storageClient).exists("storage/files/2");
    }

    @Test
    void profileAnnotationStorage() {
        assertDoesNotThrow(
                () -> applicationContext.getBean(ProfileEnabledAnnotatedTestStorage.class));
        assertThrowsWithMessage(
                NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(ProfileDisabledAnnotatedTestStorage.class),
                "No qualifying bean of type 'ch.ubique.backend.storage.spring.annotations.ProfileDisabledAnnotatedTestStorage' available");
    }
}
