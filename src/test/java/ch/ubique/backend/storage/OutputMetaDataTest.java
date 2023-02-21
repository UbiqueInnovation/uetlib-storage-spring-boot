package ch.ubique.backend.storage;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OutputMetaDataTest {

    @Test
    void fromDefaults() {}

    @Test
    void getCacheControl() {
        Assertions.assertEquals(
                "max-age=600, s-maxage=86400",
                new OutputMetaData(Duration.ofSeconds(600), Duration.ofDays(1))
                        .createCacheControl());
        Assertions.assertEquals(
                "s-maxage=86400",
                new OutputMetaData(null, Duration.ofDays(1)).createCacheControl());
        Assertions.assertEquals(
                "max-age=600",
                new OutputMetaData(Duration.ofSeconds(600), null).createCacheControl());
        Assertions.assertNull(new OutputMetaData(null, null).createCacheControl());
    }

    @Test
    void createMetaDataMap() {}
}
