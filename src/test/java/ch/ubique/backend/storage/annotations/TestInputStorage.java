package ch.ubique.backend.storage.annotations;

import static org.springframework.http.MediaType.*;

import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

public interface TestInputStorage {

    @InputMapping(path = "files/{jsonId}.json", contentType = APPLICATION_JSON_VALUE)
    TestUser getJson(@PathVariable final int jsonId) throws IOException;

    @InputMapping(path = "files/{fileId}.png", contentType = IMAGE_PNG_VALUE)
    byte[] getImage(@PathVariable final int fileId) throws IOException;

    @InputMapping(path = "files/{fileId}.png", doUncompressIfNeeded = false, contentType = IMAGE_PNG_VALUE)
    byte[] getImageCompressed(@PathVariable final int fileId) throws IOException;
}
