package ch.ubique.backend.storage.annotations;

import org.springframework.context.annotation.Profile;

@Profile("never-activated-profile-this-storage-should-not-be-instantiated")
@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation/profile",
        basePath = "storage",
        objectMapperQualifier = "testObjectMapper")
public interface ProfileDisabledAnnotatedTestStorage extends TestStorage {}
