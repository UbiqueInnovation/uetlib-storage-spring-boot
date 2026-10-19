package ch.ubique.backend.storage.spring.proxy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in HTTP facade. The owning application remains responsible for authorization. */
@RestController
@ConditionalOnProperty(
        prefix = "ch.ubique.backend.storage.proxy",
        name = "enabled",
        havingValue = "true")
public class StorageProxyController {
    private final StorageProxyClient client;

    public StorageProxyController(final StorageProxyClient client) {
        this.client = client;
    }

    @GetMapping("${ch.ubique.backend.storage.proxy.path:/v1/storage/proxy}/{*key}")
    public ResponseEntity<InputStreamResource> proxyStorageRequest(
            @RequestHeader(value = "If-None-Match", defaultValue = "") final String eTag,
            @PathVariable("key") final String key) {
        // Spring captures and decodes the remainder independently of servlet context/mount paths.
        final var storageKey = key.startsWith("/") ? key.substring(1) : key;
        if (storageKey.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return client.proxyStorageRequest(eTag, storageKey);
    }
}
