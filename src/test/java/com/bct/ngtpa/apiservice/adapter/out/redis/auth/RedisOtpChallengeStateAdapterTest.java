package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import com.bct.ngtpa.apiservice.application.service.auth.otp.Sha256OtpCodeHasher;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisOtpChallengeStateAdapterTest {

    private final OtpCodeHasher hasher = new Sha256OtpCodeHasher();
    private final RedisOtpChallengeJsonMapper jsonMapper = new RedisOtpChallengeJsonMapper();

    private CachePort cachePort;
    private RedisOtpChallengeStateAdapter adapter;

    @BeforeEach
    void setUp() {
        cachePort = org.mockito.Mockito.mock(CachePort.class);
        adapter = new RedisOtpChallengeStateAdapter(
                cachePort,
                new RedisCacheKeyFactory("ngtpa"),
                jsonMapper,
                Clock.fixed(Instant.parse("2026-06-26T10:15:30Z"), ZoneOffset.UTC));
    }

    @Test
    void buildsOtpChallengeKeyWithAuthOtpNamespace() {
        OtpChallenge challenge = sampleChallenge(Instant.parse("2026-06-26T10:20:30Z"));
        when(cachePort.set(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.save(challenge))
                .verifyComplete();

        verify(cachePort).set(eq("ngtpa:auth:otp:otp_123"), any(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void loadsOtpChallengeRoundTrip() {
        OtpChallenge challenge = sampleChallenge(Instant.parse("2026-06-26T10:20:30Z"));
        when(cachePort.get("ngtpa:auth:otp:otp_123")).thenReturn(Mono.just(Optional.of(jsonMapper.toJson(challenge))));

        StepVerifier.create(adapter.findById("otp_123"))
                .assertNext(result -> assertThat(result).contains(challenge))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenOtpChallengeMissing() {
        when(cachePort.get("ngtpa:auth:otp:missing")).thenReturn(Mono.just(Optional.empty()));

        StepVerifier.create(adapter.findById("missing"))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void updatesExistingOtpChallenge() {
        OtpChallenge updated = new OtpChallenge(
                "otp_123",
                OtpScenario.REGISTRATION,
                "workflow-123",
                OtpChannel.SMS,
                "destination-hash",
                hasher.hash("123456"),
                OtpStatus.LOCKED,
                5,
                5,
                0,
                3,
                Instant.parse("2026-06-26T10:15:30Z"),
                Instant.parse("2026-06-26T10:20:30Z"),
                Instant.parse("2026-06-26T10:15:30Z"),
                null,
                null);
        when(cachePort.set(any(), any(), any())).thenReturn(Mono.empty());
        when(cachePort.get("ngtpa:auth:otp:otp_123")).thenReturn(Mono.just(Optional.of(jsonMapper.toJson(updated))));

        StepVerifier.create(adapter.update(updated))
                .verifyComplete();

        StepVerifier.create(adapter.findById("otp_123"))
                .assertNext(result -> {
                    assertThat(result).contains(updated);
                    assertThat(result.orElseThrow().status()).isEqualTo(OtpStatus.LOCKED);
                    assertThat(result.orElseThrow().failedAttemptCount()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void deleteRemovesOtpChallenge() {
        when(cachePort.evict("ngtpa:auth:otp:otp_123")).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.delete("otp_123"))
                .verifyComplete();

        verify(cachePort).evict("ngtpa:auth:otp:otp_123");
    }

    @Test
    void saveRejectsAlreadyExpiredChallenge() {
        OtpChallenge challenge = sampleChallenge(Instant.parse("2026-06-26T10:15:30Z"));

        StepVerifier.create(adapter.save(challenge))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(cachePort);
    }

    @Test
    void saveUsesTtlDerivedFromExpiresAt() {
        OtpChallenge challenge = sampleChallenge(Instant.parse("2026-06-26T10:16:45Z"));
        when(cachePort.set(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.save(challenge))
                .verifyComplete();

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cachePort).set(eq("ngtpa:auth:otp:otp_123"), any(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(75));
    }

    @Test
    void malformedRedisJsonFailsSafely() {
        when(cachePort.get("ngtpa:auth:otp:otp_123")).thenReturn(Mono.just(Optional.of("{bad-json")));

        StepVerifier.create(adapter.findById("otp_123"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(CacheException.class);
                    assertThat(error.getMessage()).doesNotContain("{bad-json");
                })
                .verify();
    }

    @Test
    void findRejectsBlankOtpChallengeIdBeforeOutboundCall() {
        StepVerifier.create(adapter.findById("  "))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(cachePort);
    }

    private OtpChallenge sampleChallenge(Instant expiresAt) {
        return new OtpChallenge(
                "otp_123",
                OtpScenario.REGISTRATION,
                "workflow-123",
                OtpChannel.SMS,
                "destination-hash",
                hasher.hash("123456"),
                OtpStatus.CREATED,
                0,
                5,
                0,
                3,
                Instant.parse("2026-06-26T10:15:30Z"),
                expiresAt,
                Instant.parse("2026-06-26T10:15:30Z"),
                null,
                null);
    }
}