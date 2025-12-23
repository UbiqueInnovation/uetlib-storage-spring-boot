package ch.ubique.backend.storage.spring.config;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.storage.spring.annotations.EnableStorages;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.clients.FileSystemStorageClient;

import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;

@Configuration
@ComponentScan(basePackages = "ch.ubique.backend.storage.spring")
@EnableStorages(basePackages = "ch.ubique.backend.storage.spring.annotations")
public class EnableStoragesTestConfig {

    @Bean
    StorageClient testStorageClient() throws IOException {
        return new FileSystemStorageClient(Files.createTempDirectory(null).toString());
    }

    @Bean
    ObjectMapper testObjectMapper() {
        return ObjectMapperUtils.objectMapper();
    }
}
