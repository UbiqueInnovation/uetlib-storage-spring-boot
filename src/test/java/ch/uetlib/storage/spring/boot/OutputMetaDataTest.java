package ch.uetlib.storage.spring.boot;

import ch.uetlib.core.http.header.HttpHeader;
import ch.uetlib.storage.OutputMetaData;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("DataFlowIssue")
class OutputMetaDataTest {

    @Test
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
    void createMetaDataMap() {
        final var defaults = OutputMetaData.defaults();
        Assertions.assertEquals(Map.of(), defaults.createAzureMetaDataMap());
        Assertions.assertEquals(Map.of(), defaults.createMinioMetaDataMap());

        final var metadata =
                new OutputMetaData(Duration.ofDays(1), Duration.ofDays(1))
                        .nextRefresh(Instant.now().plus(Duration.ofHours(1)))
                        .bestBefore(Instant.now().plus(Duration.ofHours(4)))
                        .backoff(Duration.ofMinutes(2));
        final var azureMetaDataMap = metadata.createAzureMetaDataMap();
        final var minioMetaDataMap = metadata.createMinioMetaDataMap();
        Assertions.assertEquals(
                Set.of(
                        HttpHeader.NEXT_REFRESH.noDashesAzure(),
                        HttpHeader.BEST_BEFORE.noDashesAzure(),
                        HttpHeader.BACKOFF.noDashesAzure()),
                azureMetaDataMap.keySet());
        Assertions.assertEquals(
                Set.of(
                        HttpHeader.NEXT_REFRESH.value(),
                        HttpHeader.BEST_BEFORE.value(),
                        HttpHeader.BACKOFF.value()),
                minioMetaDataMap.keySet());
        Assertions.assertEquals(
                new HashSet<>(azureMetaDataMap.values()), new HashSet<>(minioMetaDataMap.values()));
    }
}
