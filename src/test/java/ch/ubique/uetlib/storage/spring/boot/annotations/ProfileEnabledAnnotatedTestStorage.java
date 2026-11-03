package ch.ubique.uetlib.storage.spring.boot.annotations;

import org.springframework.context.annotation.Profile;

@Profile("test")
@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation/profile",
        basePath = "storage")
public interface ProfileEnabledAnnotatedTestStorage extends TestStorage {}
