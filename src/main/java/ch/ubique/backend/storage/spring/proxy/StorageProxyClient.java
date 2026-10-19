package ch.ubique.backend.storage.spring.proxy;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

/** Streams a configured storage namespace through an application's HTTP endpoint. */
public interface StorageProxyClient {
    /**
     * @param eTag the complete If-None-Match header, or an empty string when absent
     * @param key a decoded storage key relative to the configured storage root
     * @return the streamed object, 304 when unchanged, or an error response
     */
    ResponseEntity<InputStreamResource> proxyStorageRequest(String eTag, String key);
}
