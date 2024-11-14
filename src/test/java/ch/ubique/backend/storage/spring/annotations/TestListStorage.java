package ch.ubique.backend.storage.spring.annotations;

import ch.ubique.backend.storage.annotations.ListMapping;
import ch.ubique.backend.storage.annotations.StoragePathVariable;

import org.slf4j.event.Level;

import java.io.IOException;
import java.util.Collection;

public interface TestListStorage {

    @ListMapping(path = "{folderName}/")
    Collection<String> listFiles(@StoragePathVariable final String folderName) throws IOException;

    @ListMapping(path = "files/", recursive = true, logLevel = Level.INFO)
    Collection<String> listFilesRecursive() throws IOException;
}
