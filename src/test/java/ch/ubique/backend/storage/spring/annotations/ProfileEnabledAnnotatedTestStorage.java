package ch.ubique.backend.storage.spring.annotations;

import org.springframework.context.annotation.Profile;

@Profile("test")
@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation/profile",
        basePath = "storage")
public interface ProfileEnabledAnnotatedTestStorage extends TestStorage {}
