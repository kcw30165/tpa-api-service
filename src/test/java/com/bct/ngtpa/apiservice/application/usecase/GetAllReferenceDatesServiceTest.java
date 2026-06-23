package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import com.bct.ngtpa.apiservice.application.dto.CacheEntry;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class GetAllReferenceDatesServiceTest {

    @Test
    void returnsReferenceDatesSortedByAccountEnv() {
        CacheAdminPort cacheAdminPort = mock(CacheAdminPort.class);
        when(cacheAdminPort.findAllByCapability(CacheCapability.REFERENCE_DATE))
                .thenReturn(Flux.just(
                        new CacheEntry("ngtpa:reference-date:JP", "reference-date", "JP", "31/12/2025"),
                        new CacheEntry("ngtpa:reference-date:HK", "reference-date", "HK", "01/01/2026")));

        StepVerifier.create(new GetAllReferenceDatesService(cacheAdminPort).execute())
                .assertNext(result -> assertThat(result.referenceDates())
                        .extracting("accountEnv")
                        .containsExactly("HK", "JP"))
                .verifyComplete();

        verify(cacheAdminPort).findAllByCapability(CacheCapability.REFERENCE_DATE);
    }

    @Test
    void returnsEmptyListWhenCacheHasNoReferenceDateEntries() {
        CacheAdminPort cacheAdminPort = mock(CacheAdminPort.class);
        when(cacheAdminPort.findAllByCapability(CacheCapability.REFERENCE_DATE)).thenReturn(Flux.empty());

        StepVerifier.create(new GetAllReferenceDatesService(cacheAdminPort).execute())
                .assertNext(result -> assertThat(result.referenceDates()).isEmpty())
                .verifyComplete();
    }
}
