package ch.uetlib.storage.spring.boot.annotations;

import ch.uetlib.storage.annotations.ExistsMapping;
import ch.uetlib.storage.annotations.StoragePathVariable;

import org.slf4j.event.Level;

import java.io.IOException;

public interface TestExistsStorage {

    @ExistsMapping(path = "files/{fileId}.png")
    boolean existsFile(@StoragePathVariable String fileId) throws IOException;

    @ExistsMapping(path = "files/{folderId}", logLevel = Level.ERROR)
    boolean existsPathPrefix(@StoragePathVariable int folderId) throws IOException;
}
