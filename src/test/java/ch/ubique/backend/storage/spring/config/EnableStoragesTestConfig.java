package ch.ubique.backend.storage.spring.config;

import ch.ubique.backend.spring.boot.jackson.autoconfigure.UetlibSpringBootJacksonAutoConfiguration;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.clients.FileSystemStorageClient;
import ch.ubique.backend.storage.spring.annotations.EnableStorages;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.nio.file.Files;

@SpringBootConfiguration
@ImportAutoConfiguration({
    JacksonAutoConfiguration.class,
    UetlibSpringBootJacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "ch.ubique.backend.storage.spring")
@EnableStorages(basePackages = "ch.ubique.backend.storage.spring.annotations")
public class EnableStoragesTestConfig {

    @Bean
    StorageClient testStorageClient() throws IOException {
        return new FileSystemStorageClient(Files.createTempDirectory(null).toString());
    }
}
