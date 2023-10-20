package ch.ubique.backend.storage.annotations;


import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

public final class MockedStorageRegistrarUtils {

    private MockedStorageRegistrarUtils() {}

    // package-private so we can mock it in tests
    static BuildProperties loadBuildProperties() {
            final var properties = new Properties();
            properties.setProperty("group", "ch.ubique.backend.storage.config.implementation");
            return new BuildProperties(properties);
    }
}
