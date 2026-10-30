package ch.ubique.uetlib.storage.spring.annotations;

import ch.ubique.uetlib.storage.annotations.ExistsMapping;
import ch.ubique.uetlib.storage.annotations.StoragePathVariable;
import java.io.IOException;
import org.slf4j.event.Level;

public interface TestExistsStorage {

    @ExistsMapping(path = "files/{fileId}.png")
    boolean existsFile(@StoragePathVariable String fileId) throws IOException;

    @ExistsMapping(path = "files/{folderId}", logLevel = Level.ERROR)
    boolean existsPathPrefix(@StoragePathVariable int folderId) throws IOException;
}
