package ch.uetlib.storage.spring.boot.annotations;

import ch.uetlib.storage.annotations.DeleteMapping;
import ch.uetlib.storage.annotations.StoragePathVariable;

import org.slf4j.event.Level;

import java.io.IOException;

public interface TestDeleteStorage {

    @DeleteMapping(path = "files/{fileId}.png")
    boolean deleteFile(@StoragePathVariable String fileId) throws IOException;

    @DeleteMapping(path = "files/{folderId}", recursive = true, logLevel = Level.ERROR)
    boolean deleteFolder(@StoragePathVariable int folderId) throws IOException;
}
