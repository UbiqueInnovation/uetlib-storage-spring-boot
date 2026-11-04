package ch.uetlib.storage.spring.boot.proxy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Registers the endpoint only after an application explicitly enables it and supplies a client. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "ch.ubique.backend.storage.proxy",
        name = "enabled",
        havingValue = "true")
public class StorageProxyAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    StorageProxyController storageProxyController(final StorageProxyClient client) {
        return new StorageProxyController(client);
    }
}
