package ch.ubique.uetlib.storage.spring.config;

import ch.ubique.uetlib.spring.boot.jackson.autoconfigure.UetlibSpringBootJacksonAutoConfiguration;
import ch.ubique.uetlib.storage.StorageClient;
import ch.ubique.uetlib.storage.clients.FileSystemStorageClient;
import ch.ubique.uetlib.storage.spring.annotations.EnableStorages;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@ImportAutoConfiguration({
    JacksonAutoConfiguration.class,
    UetlibSpringBootJacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "ch.ubique.uetlib.storage.spring")
@EnableStorages(basePackages = "ch.ubique.uetlib.storage.spring.annotations")
public class EnableStoragesTestConfig {

    @Bean
    StorageClient testStorageClient() throws IOException {
        return new FileSystemStorageClient(Files.createTempDirectory(null).toString());
    }
}
