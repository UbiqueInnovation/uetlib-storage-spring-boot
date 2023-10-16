package ch.ubique.backend.storage.annotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.GZipOutput;
import ch.ubique.backend.storage.output.JsonOutput;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@SpringBootTest(classes = EnableStoragesTestConfig.class)
class EnableStoragesTest {

    @SpyBean private ObjectMapper objectMapper;
    @SpyBean private StorageClient storageClient;
    @SpyBean private TestStorage testStorage;
    @SpyBean private TestStorageReferenceImplementation referenceImplementation;

    @Value("classpath:storage/files/2.png")
    private Resource png2;

    EnableStoragesTest() {}

    @BeforeEach
    void beforeEach() throws IOException {
        MockitoAnnotations.openMocks(this);
        storageClient.delete("", true);
        storageClient.write(
                new JsonOutput<>(new TestUser(17, "Professor", "Jaffe Presley")), "files/17.json");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "files/2.png");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "files/61.png");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "files/5/54.png");
        storageClient.write(new ByteOutput(png2.getContentAsByteArray()), "files/5/42.png");
    }

    @Test
    void testInputStorage() throws IOException {
        assertNotNull(testStorage);
        assertNotNull(referenceImplementation);

        final var testUser = testStorage.getJson(17);
        assertEquals(new TestUser(17, "Professor", "Jaffe Presley"), testUser);
        verify(storageClient).read("files/17.json", true);

        final var image = testStorage.getImage(2);
        assertArrayEquals(png2.getContentAsByteArray(), image);
        verify(storageClient).read("files/2.png", true);

        final var imageCompressed = testStorage.getImageCompressed(2);
        assertArrayEquals(png2.getContentAsByteArray(), imageCompressed);
        verify(storageClient).read("files/2.png", false);
    }

    @Test
    void testOutputStorage() throws IOException {
        assertNotNull(testStorage);
        assertNotNull(referenceImplementation);

        final var testUser = new TestUser(32, "Student", "Seong-Hyeon Lebogang");
        testStorage.saveJson(testUser, "32", null);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(testUser, objectMapper)),
                        "files/32.json",
                        null);
        assertEquals(
                testUser,
                objectMapper.readValue(storageClient.read("files/32.json", true), TestUser.class));

        final var outputMetaData =
                new OutputMetaData(Duration.ofSeconds(48654), Duration.ofMillis(87623))
                        .backoff(Duration.ofHours(2))
                        .nextRefresh(Instant.now().plusMillis(629235637873L));
        testStorage.saveJson(testUser, "32", outputMetaData);
        verify(storageClient)
                .write(
                        new GZipOutput(new JsonOutput<>(testUser, objectMapper)),
                        "files/32.json",
                        outputMetaData);
        assertEquals(
                testUser,
                objectMapper.readValue(storageClient.read("files/32.json", true), TestUser.class));

        testStorage.saveImage(png2.getContentAsByteArray(), "8");
        verify(storageClient)
                .write(
                        new GZipOutput(
                                new ByteOutput(png2.getContentAsByteArray(), IMAGE_PNG_VALUE)),
                        "files/8.png",
                        null);
    }

    @Test
    void testDeleteStorage() throws IOException {
        final var deleted62 = testStorage.deleteFile("62");
        verify(storageClient).delete("files/62.png", false);
        assertFalse(deleted62);
        assertEquals(
                Set.of("files/2.png", "files/5/", "files/17.json", "files/61.png"),
                storageClient.list("files/"));

        final var deleted61 = testStorage.deleteFile("61");
        verify(storageClient).delete("files/61.png", false);
        assertTrue(deleted61);
        assertFalse(
                storageClient
                        .list("files/")
                        .containsAll(Set.of("files/2.png", "files/17.json", "files/61.png")));
        assertEquals(
                Set.of("files/2.png", "files/5/", "files/17.json"), storageClient.list("files/"));

        assertEquals(storageClient.list("files/5/"), Set.of("files/5/54.png", "files/5/42.png"));
        final var deletedFolder4 = testStorage.deleteFolder(4);
        verify(storageClient).delete("files/4", true);
        assertFalse(deletedFolder4);
        assertEquals(Set.of("files/5/54.png", "files/5/42.png"), storageClient.list("files/5/"));

        final var deletedFolder5 = testStorage.deleteFolder(5);
        verify(storageClient).delete("files/5", true);
        assertTrue(deletedFolder5);
        assertThrows(NoSuchFileException.class, () -> storageClient.list("files/5/"));
        assertFalse(storageClient.list("files/").contains("files/5/"));
    }

    @Test
    void testListStorage() throws IOException {
        assertEquals(
                Set.of("files/2.png", "files/5/", "files/17.json", "files/61.png"),
                testStorage.listFiles("files"));
        verify(storageClient).list("files/", false);

        assertEquals(
                Set.of(
                        "files/2.png",
                        "files/5/54.png",
                        "files/5/42.png",
                        "files/17.json",
                        "files/61.png"),
                testStorage.listFilesRecursive());
        verify(storageClient).list("files/", true);
    }
}
