package ch.uetlib.storage.spring.boot.config;

import ch.uetlib.spring.boot.jackson.autoconfigure.UetlibSpringBootJacksonAutoConfiguration;
import ch.uetlib.storage.StorageClient;
import ch.uetlib.storage.clients.FileSystemStorageClient;
import ch.uetlib.storage.spring.boot.annotations.EnableStorages;

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
@ComponentScan(basePackages = "ch.uetlib.storage.spring.boot")
@EnableStorages(basePackages = "ch.uetlib.storage.spring.boot.annotations")
public class EnableStoragesTestConfig {

    @Bean
    StorageClient testStorageClient() throws IOException {
        return new FileSystemStorageClient(Files.createTempDirectory(null).toString());
    }
}
