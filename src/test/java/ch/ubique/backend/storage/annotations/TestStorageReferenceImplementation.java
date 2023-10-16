package ch.ubique.backend.storage.annotations;

import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.JsonOutput;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.Collection;

public final class TestStorageReferenceImplementation implements TestStorage {
    private final StorageClient storageClient;

    private final ObjectMapper objectMapper;

    private final Logger logger;

    public TestStorageReferenceImplementation(
            @Qualifier("storageClient") final StorageClient storageClient,
            final ObjectMapper objectMapper) {
        this.storageClient = storageClient;
        this.objectMapper = objectMapper;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public TestUser getJson(final int jsonId) throws IOException {
        return objectMapper.readValue(
                storageClient.read(
                        "files/{jsonId}.json".replace("{jsonId}", String.valueOf(jsonId)), true),
                TestUser.class);
    }

    @Override
    public byte[] getImage(final int fileId) throws IOException {
        return InputStreamUtils.readBytesAndClose(
                storageClient.read(
                        "files/{fileId}.jpg".replace("{fileId}", String.valueOf(fileId)), true));
    }

    @Override
    public byte[] getImageCompressed(final int fileId) throws IOException {
        return InputStreamUtils.readBytesAndClose(
                storageClient.read(
                        "files/{fileId}.jpg".replace("{fileId}", String.valueOf(fileId)), false));
    }

    @Override
    public void saveImage(final byte[] data, final String fileId) throws IOException {
        storageClient.write(
                new ByteOutput(data, "image/png").gZip(),
                "files/{fileId}.png".replace("{fileId}", fileId),
                null);
    }

    @Override
    public void saveJson(
            final TestUser json, final String jsonId, final OutputMetaData outputMetaData)
            throws IOException {
        storageClient.write(
                new JsonOutput<>(json, objectMapper).gZip(),
                "files/{jsonId}.json".replace("{jsonId}", jsonId),
                outputMetaData);
    }

    @Override
    public boolean deleteFile(final String fileId) throws IOException {
        return storageClient.delete("files/{fileId}.jpg".replace("{fileId}", fileId), false);
    }

    @Override
    public boolean deleteFolder(final int folderId) throws IOException {
        return storageClient.delete(
                "files/{folderId}".replace("{folderId}", String.valueOf(folderId)), true);
    }

    @Override
    public Collection<String> listFiles(final String folderName) throws IOException {
        return storageClient.list("{folderName}/".replace("{folderName}", folderName), false);
    }

    @Override
    public Collection<String> listFilesRecursive() throws IOException {
        return storageClient.list("files/", true);
    }
}
