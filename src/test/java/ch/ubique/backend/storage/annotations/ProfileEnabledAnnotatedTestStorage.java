package ch.ubique.backend.storage.annotations;

import org.springframework.context.annotation.Profile;

@Profile("test")
@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation/profile",
        basePath = "storage",
        objectMapperQualifier = "testObjectMapper")
public interface ProfileEnabledAnnotatedTestStorage extends TestStorage {}
