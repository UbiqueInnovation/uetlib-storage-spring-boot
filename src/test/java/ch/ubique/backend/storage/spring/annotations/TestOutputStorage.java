package ch.ubique.backend.storage.spring.annotations;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.StorageObject;
import ch.ubique.backend.storage.annotations.StoragePathVariable;

import org.slf4j.event.Level;

import java.io.IOException;

public interface TestOutputStorage {

    @OutputMapping(
            path = "files/{fileId}.png",
            contentType = IMAGE_PNG_VALUE,
            logLevel = Level.WARN)
    void saveImage(@StorageObject byte[] data, @StoragePathVariable final String fileId)
            throws IOException;

    @OutputMapping(
            path = "files/{jsonId}.json",
            contentType = APPLICATION_JSON_VALUE,
            documentedClass = TestUser.class,
            logLevel = Level.TRACE)
    void saveJson(
            @StorageObject TestUser json,
            @StoragePathVariable final String jsonId,
            OutputMetaData outputMetaData)
            throws IOException;
}
