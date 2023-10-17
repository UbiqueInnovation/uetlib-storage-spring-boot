package ch.ubique.backend.storage.annotations;

@Storage(clientQualifier = "storageClient", basePath = "storage")
public interface AnnotatedTestStorage extends TestStorage {}
