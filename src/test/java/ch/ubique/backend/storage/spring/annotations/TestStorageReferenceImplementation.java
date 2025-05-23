package ch.ubique.backend.storage.spring.annotations;

import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.JsonOutput;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;

public final class TestStorageReferenceImplementation implements TestStorage {

    private final StorageClient storageClient;

    private final ObjectMapper objectMapper;

    public TestStorageReferenceImplementation(
            final StorageClient storageClient, final ObjectMapper objectMapper) {
        this.storageClient = storageClient;
        this.objectMapper = objectMapper;
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
                        "files/{fileId}.png".replace("{fileId}", String.valueOf(fileId)), true));
    }

    @Override
    public byte[] getImageCompressed(final int fileId) throws IOException {
        return InputStreamUtils.readBytesAndClose(
                storageClient.read(
                        "files/{fileId}.png".replace("{fileId}", String.valueOf(fileId)), false));
    }

    @Override
    public void saveImage(final byte[] data, final String fileId) throws IOException {
        storageClient.write(
                new ByteOutput(data, "image/png").gZip(),
                "files/{fileId}.png".replace("{fileId}", fileId),
                null);
    }

    @Override
    public void saveImageByteOutput(final ByteOutput byteOutput, final String fileId) throws IOException {
        storageClient.write(
                byteOutput.gZip(),
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
    public void saveJsonOutput(
            final JsonOutput<TestUser> jsonOutput, final String jsonId, final OutputMetaData outputMetaData)
            throws IOException {
        storageClient.write(
                jsonOutput.gZip(),
                "files/{jsonId}.json".replace("{jsonId}", jsonId),
                outputMetaData);
    }

    @Override
    public boolean deleteFile(final String fileId) throws IOException {
        return storageClient.delete("files/{fileId}.png".replace("{fileId}", fileId), false);
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

    @Override
    public boolean existsFile(final String fileId) throws IOException {
        return storageClient.exists("files/{fileId}.png");
    }

    @Override
    public boolean existsPathPrefix(final int folderId) throws IOException {
        return storageClient.exists("files/{folderId}");
    }
}
