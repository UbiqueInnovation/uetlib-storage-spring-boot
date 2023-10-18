package ch.ubique.backend.storage.annotations;

import org.slf4j.event.Level;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

public interface TestDeleteStorage {

    @DeleteMapping(path = "files/{fileId}.png")
    boolean deleteFile(@PathVariable String fileId) throws IOException;

    @DeleteMapping(path = "files/{folderId}", recursive = true, logLevel = Level.ERROR)
    boolean deleteFolder(@PathVariable int folderId) throws IOException;
}
