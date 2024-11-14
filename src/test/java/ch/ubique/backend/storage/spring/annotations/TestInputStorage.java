package ch.ubique.backend.storage.spring.annotations;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import ch.ubique.backend.storage.annotations.InputMapping;
import ch.ubique.backend.storage.annotations.StoragePathVariable;

import org.slf4j.event.Level;

import java.io.IOException;

public interface TestInputStorage {

    @InputMapping(
            path = "files/{jsonId}.json",
            contentType = APPLICATION_JSON_VALUE,
            logLevel = Level.DEBUG)
    TestUser getJson(@StoragePathVariable final int jsonId) throws IOException;

    @InputMapping(path = "files/{fileId}.png", contentType = IMAGE_PNG_VALUE, logLevel = Level.INFO)
    byte[] getImage(@StoragePathVariable final int fileId) throws IOException;

    @InputMapping(
            path = "files/{fileId}.png",
            doUncompressIfNeeded = false,
            contentType = IMAGE_PNG_VALUE)
    byte[] getImageCompressed(@StoragePathVariable final int fileId) throws IOException;
}
