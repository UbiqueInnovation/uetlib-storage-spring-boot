package ch.ubique.uetlib.storage.spring.annotations;

import ch.ubique.uetlib.storage.annotations.ListMapping;
import ch.ubique.uetlib.storage.annotations.StoragePathVariable;
import java.io.IOException;
import java.util.Collection;
import org.slf4j.event.Level;

public interface TestListStorage {

    @ListMapping(path = "{folderName}/")
    Collection<String> listFiles(@StoragePathVariable final String folderName) throws IOException;

    @ListMapping(path = "files/", recursive = true, logLevel = Level.INFO)
    Collection<String> listFilesRecursive() throws IOException;
}
