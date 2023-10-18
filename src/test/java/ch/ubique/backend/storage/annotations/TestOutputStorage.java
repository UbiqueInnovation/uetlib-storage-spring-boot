package ch.ubique.backend.storage.annotations;

import static org.springframework.http.MediaType.*;

import ch.ubique.backend.storage.OutputMetaData;

import org.slf4j.event.Level;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

public interface TestOutputStorage {

    @OutputMapping(
            path = "files/{fileId}.png",
            contentType = IMAGE_PNG_VALUE,
            logLevel = Level.WARN)
    void saveImage(@StorageObject byte[] data, @PathVariable final String fileId)
            throws IOException;

    @OutputMapping(
            path = "files/{jsonId}.json",
            contentType = APPLICATION_JSON_VALUE,
            documentedClass = TestUser.class,
            logLevel = Level.TRACE)
    void saveJson(
            @StorageObject TestUser json,
            @PathVariable final String jsonId,
            OutputMetaData outputMetaData)
            throws IOException;
}
