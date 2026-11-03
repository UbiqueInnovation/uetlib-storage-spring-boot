package ch.ubique.uetlib.storage.spring.boot.annotations;

import org.springframework.context.annotation.Profile;

@Profile("never-activated-profile-this-storage-should-not-be-instantiated")
@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation/profile",
        basePath = "storage")
public interface ProfileDisabledAnnotatedTestStorage extends TestStorage {}
