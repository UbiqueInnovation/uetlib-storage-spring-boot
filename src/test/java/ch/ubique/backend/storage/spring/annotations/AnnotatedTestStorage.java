package ch.ubique.backend.storage.spring.annotations;

@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation",
        basePath = "storage")
public interface AnnotatedTestStorage extends TestStorage {}
