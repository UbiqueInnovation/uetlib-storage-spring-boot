package ch.ubique.uetlib.storage.spring.boot.annotations;

import ch.ubique.uetlib.storage.annotations.ExistsMapping;
import ch.ubique.uetlib.storage.annotations.StoragePathVariable;

import org.slf4j.event.Level;

import java.io.IOException;

public interface TestExistsStorage {

    @ExistsMapping(path = "files/{fileId}.png")
    boolean existsFile(@StoragePathVariable String fileId) throws IOException;

    @ExistsMapping(path = "files/{folderId}", logLevel = Level.ERROR)
    boolean existsPathPrefix(@StoragePathVariable int folderId) throws IOException;
}
