package ch.ubique.backend.storage;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OutputMetaDataTest {

    @Test
    void fromDefaults() {}

    @Test
    @SuppressWarnings("java:S5778")
    void getCacheControl() {
        Assertions.assertEquals(
                "max-age=600, s-maxage=86400",
                new OutputMetaData(Duration.ofSeconds(600), Duration.ofDays(1))
                        .createCacheControl());
        Assertions.assertEquals(
                "max-age=600, s-maxage=600",
                new OutputMetaData(Duration.ofSeconds(600), null).createCacheControl());
        Assertions.assertThrows(
                NullPointerException.class, () -> new OutputMetaData(null, Duration.ofDays(1)));
        Assertions.assertThrows(NullPointerException.class, () -> new OutputMetaData(null, null));
    }

    @Test
    void createMetaDataMap() {}
}
