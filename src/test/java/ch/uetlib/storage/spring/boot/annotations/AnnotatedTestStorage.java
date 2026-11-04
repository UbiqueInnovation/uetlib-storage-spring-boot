package ch.uetlib.storage.spring.boot.annotations;

@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation",
        basePath = "storage")
public interface AnnotatedTestStorage extends TestStorage {}
