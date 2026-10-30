package ch.ubique.uetlib.storage.spring.annotations;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import ch.ubique.uetlib.storage.OutputMetaData;
import ch.ubique.uetlib.storage.annotations.OutputMapping;
import ch.ubique.uetlib.storage.annotations.StorageObject;
import ch.ubique.uetlib.storage.annotations.StoragePathVariable;
import ch.ubique.uetlib.storage.output.ByteOutput;
import ch.ubique.uetlib.storage.output.JsonOutput;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

public interface TestOutputStorage {

    @OutputMapping(
            path = "files/{fileId}.png",
            contentType = IMAGE_PNG_VALUE,
            logLevel = Level.WARN)
    void saveImage(@StorageObject byte[] data, @StoragePathVariable final String fileId)
            throws IOException;

    @OutputMapping(
            path = "files/output-{fileId}.png",
            contentType = IMAGE_PNG_VALUE,
            logLevel = Level.WARN)
    void saveImageByteOutput(
            @StorageObject ByteOutput byteOutput, @StoragePathVariable final String fileId)
            throws IOException;

    @OutputMapping(
            path = "files/{jsonId}.json.gz",
            contentType = APPLICATION_JSON_VALUE,
            logLevel = Level.TRACE)
    void saveJson(
            @StorageObject TestUser json,
            @StoragePathVariable final String jsonId,
            @Nullable OutputMetaData outputMetaData)
            throws IOException;

    @OutputMapping(
            path = "files/maps/{mapId}.json",
            contentType = "application/json",
            logLevel = Level.TRACE)
    void saveMap(
            @StorageObject Map<Integer, Object> map,
            @StoragePathVariable @NotBlank String mapId,
            @Nullable OutputMetaData outputMetaData)
            throws IOException;

    @OutputMapping(
            path = "files/{jsonId}.json",
            contentType = APPLICATION_JSON_VALUE,
            logLevel = Level.TRACE,
            gZip = false)
    void saveJsonOutput(
            @StorageObject JsonOutput<TestUser> jsonOutput,
            @StoragePathVariable final String jsonId,
            @Nullable OutputMetaData outputMetaData)
            throws IOException;
}
