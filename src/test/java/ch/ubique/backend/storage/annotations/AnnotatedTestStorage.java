package ch.ubique.backend.storage.annotations;

@Storage(
        clientQualifier = "testStorageClient",
        documentationBaseUrl = "documentation",
        basePath = "storage",
        objectMapperQualifier = "testObjectMapper")
public interface AnnotatedTestStorage extends TestStorage {}
