package ch.uetlib.storage.spring.boot.proxy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StorageProxyControllerTest {
    @Test
    void forwardsNestedKeysAndTheCompleteEtagHeader() throws Exception {
        final var key = new AtomicReference<@Nullable String>();
        final var etag = new AtomicReference<@Nullable String>();
        final StorageProxyClient client =
                (header, path) -> {
                    key.set(path);
                    etag.set(header);
                    return ResponseEntity.ok()
                            .body(
                                    new InputStreamResource(
                                            new ByteArrayInputStream(new byte[] {1, 2, 3})));
                };
        final var mvc = MockMvcBuilders.standaloneSetup(new StorageProxyController(client)).build();
        mvc.perform(
                        get("/v1/storage/proxy/v1/land/0/5/summary.json")
                                .header("If-None-Match", "W/\"old\", \"new\""))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
        assertThat(key.get()).isEqualTo("v1/land/0/5/summary.json");
        assertThat(etag.get()).isEqualTo("W/\"old\", \"new\"");
    }

    @Test
    void handlesServletContextPathsAndEncodedFilenames() throws Exception {
        final var key = new AtomicReference<@Nullable String>();
        final StorageProxyClient client =
                (_, path) -> {
                    key.set(path);
                    return ResponseEntity.notFound().build();
                };
        final var mvc = MockMvcBuilders.standaloneSetup(new StorageProxyController(client)).build();
        mvc.perform(
                        get(URI.create("/app/v1/storage/proxy/folder/hello%20world.txt"))
                                .contextPath("/app"))
                .andExpect(status().isNotFound());
        assertThat(key.get()).isEqualTo("folder/hello world.txt");
    }

    @Test
    void configurablePathDoesNotDependOnHardcodedPrefixExtraction() throws Exception {
        final var key = new AtomicReference<@Nullable String>();
        final StorageProxyClient client =
                (_, path) -> {
                    key.set(path);
                    return ResponseEntity.notFound().build();
                };
        final var mvc =
                MockMvcBuilders.standaloneSetup(new StorageProxyController(client))
                        .addPlaceholderValue("ch.uetlib.storage.proxy.path", "/files")
                        .build();
        mvc.perform(get("/files/v1/data.json")).andExpect(status().isNotFound());
        assertThat(key.get()).isEqualTo("v1/data.json");
        mvc.perform(get("/files/")).andExpect(status().isNotFound());
    }

    @Test
    void endpointIsDisabledByDefaultEvenWhenAClientExists() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageProxyAutoConfiguration.class))
                .withBean(
                        StorageProxyClient.class, () -> (_, _) -> ResponseEntity.notFound().build())
                .run(context -> assertThat(context).doesNotHaveBean(StorageProxyController.class));
    }

    @Test
    void endpointCanBeEnabledExplicitly() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageProxyAutoConfiguration.class))
                .withPropertyValues("ch.uetlib.storage.proxy.enabled=true")
                .withBean(
                        StorageProxyClient.class, () -> (_, _) -> ResponseEntity.notFound().build())
                .run(context -> assertThat(context).hasSingleBean(StorageProxyController.class));
    }

    @Test
    void enablingWithoutAClientFailsInsteadOfExposingADefaultStorageRoot() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageProxyAutoConfiguration.class))
                .withPropertyValues("ch.uetlib.storage.proxy.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
