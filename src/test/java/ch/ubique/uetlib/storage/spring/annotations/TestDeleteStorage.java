package ch.ubique.uetlib.storage.spring.annotations;

import ch.ubique.uetlib.storage.annotations.DeleteMapping;
import ch.ubique.uetlib.storage.annotations.StoragePathVariable;
import java.io.IOException;
import org.slf4j.event.Level;

public interface TestDeleteStorage {

    @DeleteMapping(path = "files/{fileId}.png")
    boolean deleteFile(@StoragePathVariable String fileId) throws IOException;

    @DeleteMapping(path = "files/{folderId}", recursive = true, logLevel = Level.ERROR)
    boolean deleteFolder(@StoragePathVariable int folderId) throws IOException;
}
