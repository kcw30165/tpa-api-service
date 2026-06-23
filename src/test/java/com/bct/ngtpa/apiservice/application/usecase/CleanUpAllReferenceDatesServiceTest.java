package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CleanUpAllReferenceDatesServiceTest {

    @Test
    void returnsDeletedCountFromCacheAdminPort() {
        CacheAdminPort cacheAdminPort = mock(CacheAdminPort.class);
        when(cacheAdminPort.evictAllByCapability(CacheCapability.REFERENCE_DATE)).thenReturn(Mono.just(2L));

        StepVerifier.create(new CleanUpAllReferenceDatesService(cacheAdminPort).execute())
                .assertNext(result -> assertThat(result.deletedCount()).isEqualTo(2L))
                .verifyComplete();

        verify(cacheAdminPort).evictAllByCapability(CacheCapability.REFERENCE_DATE);
    }

    @Test
    void keepsZeroDeletedCountAsSuccessfulResult() {
        CacheAdminPort cacheAdminPort = mock(CacheAdminPort.class);
        when(cacheAdminPort.evictAllByCapability(CacheCapability.REFERENCE_DATE)).thenReturn(Mono.just(0L));

        StepVerifier.create(new CleanUpAllReferenceDatesService(cacheAdminPort).execute())
                .assertNext(result -> assertThat(result.deletedCount()).isZero())
                .verifyComplete();
    }
}
