package ch.ubique.backend.storage.config.documentation;

import ch.ubique.backend.storage.annotations.EnableStoragesDocumentation;

import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

@EnableStoragesDocumentation(basePackages = "ch.ubique.backend.storage.annotations")
public class EnableStoragesDocumentationTestConfig {

    @Bean
    BuildProperties buildProperties() {
        final var properties = new Properties();
        properties.setProperty("group", "ch.ubique.backend.storage.config.documentation");
        return new BuildProperties(properties);
    }
}
