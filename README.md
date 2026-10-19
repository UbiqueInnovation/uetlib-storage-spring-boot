# Uetlib Storage Spring

## Changelog

See [CHANGELOG.md](CHANGELOG.md)

## Storage HTTP proxy

An opt-in MVC endpoint can serve the same keys used by your storage output mappings. The default
URL is `/v1/storage/proxy/{key}`. It is **disabled by default**, creates no storage clients, and
makes no assumptions about Spring profiles, credentials or which files a user may access.
Applications must configure their own security rules or implement authorization in their client.

Enable it in the appropriate application/profile configuration:

```properties
ch.ubique.backend.storage.proxy.enabled=true
# Optional; no trailing slash:
ch.ubique.backend.storage.proxy.path=/v1/storage/proxy
```

Provide exactly one `StorageProxyClient` bean (or a primary bean). For filesystem output:

```java
@Bean
@Profile("local-output")
StorageProxyClient storageProxyClient() {
    return new LocalStorageProxyClient(Path.of("/tmp/my-application"));
}
```

`GET /v1/storage/proxy/v1/land/summary.json` then streams
`/tmp/my-application/v1/land/summary.json`. No application-specific path prefix is added.
Keep the endpoint disabled in other profiles unless a suitable client is configured there.
Enabling it without a client fails startup instead of silently exposing a default directory.

Files are streamed with ETag, Last-Modified, Content-Type and Content-Length. Local files use
`Cache-Control: no-cache`; matching If-None-Match (including weak validators, lists and `*`)
returns 304 without opening the body. Missing files, directories, invalid paths, absolute keys,
traversal and symlinks escaping the root return 404. The filesystem root must remain under
trusted application/operator control; this is not isolation from concurrent malicious edits.

Extension mappings can be supplied to the local client when the host OS does not recognize a
format:

```java
new LocalStorageProxyClient(root, Map.of("custom", MediaType.parseMediaType("application/x-custom")));
```

Servlet context paths, nested keys and URL-encoded filenames are handled by Spring MVC. The
proxy does not implement range requests, uploads or per-user authorization.

### Migrating existing applications

The controllers in Ridian and SchweizMobil both use `/v1/storage/proxy/**` and manually remove
the prefix from the raw request URI. Their local clients contain the same filesystem logic;
SchweizMobil enables it only for `local-output`, while Ridian also selects an S3/R2 implementation
for other profiles.

To migrate the local path, replace the application's controller/local proxy client with the
configuration above and reuse its existing local-output root. Keep the application's profile
selection and security configuration. Leave any remote proxy implementation registered through
the shared `StorageProxyClient` contract until its provider adapter is migrated.
