package ch.ubique.backend.storage.annotations;

@Storage(clientQualifier = "storageClient", baseUrl = "documentation", basePath = "storage")
public interface AnnotatedTestStorage extends TestStorage {}
