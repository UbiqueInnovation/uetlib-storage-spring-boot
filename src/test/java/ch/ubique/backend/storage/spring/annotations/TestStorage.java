package ch.ubique.backend.storage.spring.annotations;

public interface TestStorage
        extends TestOutputStorage,
                TestInputStorage,
                TestListStorage,
                TestDeleteStorage,
                TestExistsStorage {}
