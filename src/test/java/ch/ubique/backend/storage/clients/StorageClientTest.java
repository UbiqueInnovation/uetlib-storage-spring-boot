package ch.ubique.backend.storage.clients;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

class StorageClientTest {

    @NotNull
    private static String readStringAndClose(final InputStream storageClient) throws IOException {
        return new String(
                InputStreamUtils.readBytesAndClose(storageClient), StandardCharsets.UTF_8);
    }

    @Test
    void testFileSystemStorageClient() throws IOException {
        final var tempDir = Files.createTempDirectory(null);
        final var storageClient = new FileSystemStorageClient(tempDir.toString());

        test(storageClient);

        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    void testMinioStorageClient() throws IOException {
        try (final var minioServer = new GenericContainer<>("minio/minio")) {
            minioServer
                    .withEnv("MINIO_ACCESS_KEY", "test")
                    .withEnv("MINIO_SECRET_KEY", "test1234")
                    .withCommand("server /data")
                    .withExposedPorts(9000)
                    .waitingFor(
                            new HttpWaitStrategy()
                                    .forPath("/minio/health/ready")
                                    .forPort(9000)
                                    .withStartupTimeout(Duration.ofSeconds(10)))
                    .start();

            final var mappedPort = minioServer.getFirstMappedPort();
            Testcontainers.exposeHostPorts(mappedPort);
            final var minioServerUrl =
                    String.format("http://%s:%s", minioServer.getHost(), mappedPort);

            final var storageClient =
                    new MinioStorageClient(
                            "test", minioServerUrl, "test", "test1234", true, null, null);

            storageClient.createBucket("test");

            test(storageClient);

            minioServer.stop();
        }
    }

    @Test
    void testAzureBlobStorageClient() throws IOException {
        try (final var azureBlobStorageContainer =
                new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite")) {
            azureBlobStorageContainer.withExposedPorts(10000).start();

            final var mappedPort = azureBlobStorageContainer.getFirstMappedPort();
            Testcontainers.exposeHostPorts(mappedPort);

            final var connectionString =
                    String.format(
                            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://%s:%s/devstoreaccount1;",
                            azureBlobStorageContainer.getHost(), mappedPort);

            final var storageClient = new AzureBlobStorageClient("test", connectionString);

            storageClient.createContainer("test");

            test(storageClient);

            azureBlobStorageContainer.stop();
        }
    }

    private void test(final StorageClient storageClient) throws IOException {

        if (!(storageClient instanceof RetryStorageClient)) {
            test(
                    storageClient.retryable(
                            new RetryTemplateBuilder()
                                    .maxAttempts(2)
                                    .fixedBackoff(Duration.ofMillis(1))
                                    .build())); // Also test retryable version
        }

        assertEquals(Set.of(), storageClient.list());

        storageClient.write(new ByteOutput("Hello World".getBytes(StandardCharsets.UTF_8)), "foo");
        assertEquals(Set.of("foo"), storageClient.list());

        assertEquals("Hello World", readStringAndClose(storageClient.read("foo")));

        assertEquals(
                "Hello World",
                readStringAndClose(
                        URI.create(
                                        storageClient.getSignedUrl(
                                                "foo", OffsetDateTime.now().plusHours(1)))
                                .toURL()
                                .openStream()));

        storageClient.write(new ByteOutput(new byte[0]), "bar/foo1");
        storageClient.write(new ByteOutput(new byte[0]), "bar/foo2");

        assertTrue(storageClient.exists("foo"));
        assertTrue(storageClient.exists("bar/"));
        assertTrue(storageClient.exists("bar/foo1"));
        assertTrue(storageClient.exists("bar/foo2"));
        assertFalse(storageClient.exists("nonExisting"));

        assertEquals(Set.of("foo", "bar/"), storageClient.list());
        assertEquals(Set.of("foo", "bar/foo1", "bar/foo2"), storageClient.list(true));
        assertEquals(Set.of("bar/foo1", "bar/foo2"), storageClient.list("bar", true));

        assertDoesNotThrow(() -> storageClient.delete("bar/foo1"));
        assertFalse(storageClient.exists("bar/foo1"));
        assertEquals(Set.of("foo", "bar/foo2"), storageClient.list(true));

        assertThrows(IOException.class, () -> storageClient.delete("bar"));
        assertTrue(storageClient.exists("bar/"));
        assertTrue(storageClient.exists("bar/foo2"));
        assertEquals(Set.of("foo", "bar/"), storageClient.list());

        assertDoesNotThrow(() -> storageClient.delete("bar", true));
        assertTrue(storageClient.exists("foo"));
        assertFalse(storageClient.exists("bar/"));
        assertFalse(storageClient.exists("bar/foo1"));
        assertFalse(storageClient.exists("bar/foo2"));
        assertEquals(Set.of("foo"), storageClient.list());

        assertDoesNotThrow(() -> storageClient.delete("foo"));
        assertTrue(storageClient.list().isEmpty());
    }
}
