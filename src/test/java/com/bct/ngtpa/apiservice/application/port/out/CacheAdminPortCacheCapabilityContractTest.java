package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CacheAdminPortCacheCapabilityContractTest {

    @Test
    void adminOperationsAcceptCacheCapabilityEnumNotRawPattern() {
        var find = Arrays.stream(CacheAdminPort.class.getMethods())
                .filter(method -> method.getName().equals("findAllByCapability"))
                .findFirst()
                .orElseThrow();
        var evict = Arrays.stream(CacheAdminPort.class.getMethods())
                .filter(method -> method.getName().equals("evictAllByCapability"))
                .findFirst()
                .orElseThrow();

        assertThat(find.getParameterTypes()).containsExactly(CacheCapability.class);
        assertThat(evict.getParameterTypes()).containsExactly(CacheCapability.class);
    }
}
