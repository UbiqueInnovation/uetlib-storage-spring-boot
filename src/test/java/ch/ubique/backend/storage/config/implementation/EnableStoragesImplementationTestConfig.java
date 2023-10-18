package ch.ubique.backend.storage.config.implementation;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.EnableStorages;
import ch.ubique.backend.storage.clients.FileSystemStorageClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

@EnableStorages(basePackages = "ch.ubique.backend.storage.annotations")
public class EnableStoragesImplementationTestConfig {

    @Bean
    StorageClient storageClient() throws IOException {
        return new FileSystemStorageClient(Files.createTempDirectory(null).toString());
    }

    @Bean
    ObjectMapper objectMapper() {
        return ObjectMapperUtils.objectMapper();
    }

    @Bean
    BuildProperties buildProperties() {
        final var properties = new Properties();
        properties.setProperty("group", "ch.ubique.backend.storage.config.implementation");
        return new BuildProperties(properties);
    }
}
