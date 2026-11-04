package ch.uetlib.storage.spring.boot.annotations;

public interface TestStorage
        extends TestOutputStorage,
                TestInputStorage,
                TestListStorage,
                TestDeleteStorage,
                TestExistsStorage {}
