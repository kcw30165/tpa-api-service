package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReferenceDateAdapterTest {
    private static final Clock CLOCK=Clock.fixed(LocalDate.of(2026,6,21).atTime(16,30).atZone(ZoneId.of("UTC")).toInstant(),ZoneId.of("UTC"));
    @Test void productionReturnsHongKongDateWithoutCache() {
        CachePort cache=mock(CachePort.class); var adapter=adapter("PROD",Optional.of(cache));
        StepVerifier.create(adapter.resolveReferenceDate("JP")).expectNext(LocalDate.of(2026,6,22)).verifyComplete();
        verifyNoInteractions(cache);
    }
    @Test void validCacheValueIsReturned() {
        CachePort cache=mock(CachePort.class); when(cache.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.of("22/06/2026")));
        StepVerifier.create(adapter("SIT",Optional.of(cache)).resolveReferenceDate("JP")).expectNext(LocalDate.of(2026,6,22)).verifyComplete();
        verify(cache).get("ngtpa:reference-date:JP");
    }
    @Test void cacheMissFallsBackToHongKongDate() {
        CachePort cache=mock(CachePort.class); when(cache.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        StepVerifier.create(adapter("SIT",Optional.of(cache)).resolveReferenceDate("JP")).expectNext(LocalDate.of(2026,6,22)).verifyComplete();
    }
    @Test void blankInvalidAndReadFailureFallBackToHongKongDate() {
        for (Mono<Optional<String>> value : java.util.List.<Mono<Optional<String>>>of(Mono.just(Optional.of(" ")),Mono.just(Optional.of("2026-06-22")),Mono.<Optional<String>>error(new CacheException("Cache get operation failed")))) {
            CachePort cache=mock(CachePort.class); when(cache.get("ngtpa:reference-date:JP")).thenReturn(value);
            StepVerifier.create(adapter("SIT",Optional.of(cache)).resolveReferenceDate("JP")).expectNext(LocalDate.of(2026,6,22)).verifyComplete();
        }
    }
    @Test void disabledCacheFallsBackToHongKongDate() {
        StepVerifier.create(adapter("SIT",Optional.empty()).resolveReferenceDate("JP")).expectNext(LocalDate.of(2026,6,22)).verifyComplete();
    }
    private ReferenceDateAdapter adapter(String profile,Optional<CachePort> cache) {
        var props=new ReferenceDateProperties(); var env=new MockEnvironment(); env.setActiveProfiles(profile);
        return new ReferenceDateAdapter(props,cache,"ngtpa",env,CLOCK);
    }
}
