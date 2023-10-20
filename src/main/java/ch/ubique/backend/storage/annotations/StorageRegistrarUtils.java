package ch.ubique.backend.storage.annotations;

import com.pivovarit.function.exception.WrappedException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public final class StorageRegistrarUtils {

    private StorageRegistrarUtils() {}

    // package-private so we can mock it in tests
    static BuildProperties loadBuildProperties() {
        final var buildPropertiesResource = new ClassPathResource("META-INF/build-info.properties");
        if (!buildPropertiesResource.exists()) {
            throw new IllegalStateException(
                    "File META-INF/build-info.properties does not exist. Did the build-info target in the spring-boot-maven-plugin run?");
        }

        final var loadedProperties = new Properties();
        try {
            loadedProperties.load(buildPropertiesResource.getInputStream());
        } catch (final IOException e) {
            throw new WrappedException(e);
        }

        // keys are prefixed with "build." -> need to remove that prefix
        final var mappedProperties =
                loadedProperties.entrySet().stream()
                        .map(entry -> Map.entry((String) entry.getKey(), (String) entry.getValue()))
                        .collect(
                                Collectors.toMap(
                                        entry -> StringUtils.removeStart(entry.getKey(), "build."),
                                        Map.Entry::getValue));

        final var properties = new Properties();
        properties.putAll(mappedProperties);
        return new BuildProperties(properties);
    }
}
