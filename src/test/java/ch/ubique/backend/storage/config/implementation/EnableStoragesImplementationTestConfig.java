package ch.ubique.backend.storage.config.implementation;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.EnableStorages;
import ch.ubique.backend.storage.clients.FileSystemStorageClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;

@EnableStorages(basePackages = "ch.ubique.backend.storage.annotations")
public class EnableStoragesImplementationTestConfig {

    @Bean
    @Qualifier("testStorageClient")
    StorageClient storageClient() throws IOException {
        return new FileSystemStorageClient(Files.createTempDirectory(null).toString());
    }

    @Bean
    @Qualifier("testObjectMapper")
    ObjectMapper objectMapper() {
        return ObjectMapperUtils.objectMapper();
    }
}
