package ch.ubique.backend.storage.annotations;

import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.Collection;

public interface TestListStorage {

    @ListMapping(path = "{folderName}/")
    Collection<String> listFiles(@PathVariable final String folderName) throws IOException;

    @ListMapping(path = "files/", recursive = true)
    Collection<String> listFilesRecursive() throws IOException;
}
