package com.bct.ngtpa.apiservice.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CacheCapabilityTest {

    @Test
    void referenceDateUsesExistingRedisCapabilitySegment() {
        assertThat(CacheCapability.REFERENCE_DATE.keyPart()).isEqualTo("reference-date");
    }

    @Test
    void capabilityKeyPartsAreNonBlankAndUnique() {
        var keyParts = Arrays.stream(CacheCapability.values())
                .map(CacheCapability::keyPart)
                .toList();

        assertThat(keyParts).allSatisfy(keyPart -> assertThat(keyPart).isNotBlank());
        assertThat(keyParts).doesNotHaveDuplicates();
    }
}
