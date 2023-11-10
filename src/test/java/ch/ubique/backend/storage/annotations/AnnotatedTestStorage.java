package ch.ubique.backend.storage.annotations;

@Storage(
        clientQualifier = "testStorageClient",
        baseUrl = "documentation",
        basePath = "storage",
        objectMapperQualifier = "testObjectMapper")
public interface AnnotatedTestStorage extends TestStorage {}
