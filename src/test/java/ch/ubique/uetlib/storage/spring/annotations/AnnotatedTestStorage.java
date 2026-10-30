package ch.ubique.uetlib.storage.spring.annotations;

@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation",
        basePath = "storage")
public interface AnnotatedTestStorage extends TestStorage {}
