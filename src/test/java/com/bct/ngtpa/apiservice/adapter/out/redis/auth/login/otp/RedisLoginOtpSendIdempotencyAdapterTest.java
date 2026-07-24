package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisLoginOtpSendIdempotencyAdapterTest {
    static final Instant NOW = Instant.parse("2026-07-24T03:00:00Z");
    static final Duration TTL = Duration.ofSeconds(300);
    static final LoginOtpSendRequestHash HASH = new LoginOtpSendRequestHash("a".repeat(64));

    @Test
    void atomicallyReservesAndUsesOpaqueKeyOnly() {
        var ops = new MemoryOps();
        var adapter = adapter(ops);
        StepVerifier.create(adapter.reserve(new LoginOtpSendReserveCommand("idem_key_123", HASH, NOW, TTL)))
                .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpSendReservationOutcome.STARTED))
                .verifyComplete();
        assertThat(ops.lastKey).isEqualTo("ngtpa:auth:idempotency:login:otp-send:idem_key_123");
        assertThat(ops.lastKey).doesNotContain("afs_", "dst_", "csrf", "@", "91234567");
        assertThat(ops.lastValue).doesNotContain("afs_", "dst_", "csrf", "@", "91234567", "123456");
    }

    @Test
    void classifiesExistingLifecycleAndRequestHash() {
        var ops = new MemoryOps();
        var adapter = adapter(ops);
        var reserve = new LoginOtpSendReserveCommand("idem_key_123", HASH, NOW, TTL);
        adapter.reserve(reserve).block();
        assertThat(adapter.reserve(reserve).block().outcome())
                .isEqualTo(LoginOtpSendReservationOutcome.DUPLICATE_IN_PROGRESS);
        adapter.complete(new LoginOtpSendCompleteCommand("idem_key_123", HASH,
                new LoginOtpSendReplaySnapshot("****4567", 300, 60), NOW, TTL)).block();
        var replay = adapter.reserve(reserve).block();
        assertThat(replay.outcome()).isEqualTo(LoginOtpSendReservationOutcome.COMPLETED_REPLAY);
        assertThat(replay.replay().maskedDestination()).isEqualTo("****4567");
        var other = new LoginOtpSendRequestHash("b".repeat(64));
        assertThat(adapter.reserve(new LoginOtpSendReserveCommand("idem_key_123", other, NOW, TTL)).block().outcome())
                .isEqualTo(LoginOtpSendReservationOutcome.KEY_REUSED);
    }

    @Test
    void failedAndUnknownTransitionsAreReplaySafe() {
        var ops = new MemoryOps();
        var adapter = adapter(ops);
        var reserve = new LoginOtpSendReserveCommand("idem_key_123", HASH, NOW, TTL);
        adapter.reserve(reserve).block();
        adapter.fail(new LoginOtpSendTerminalCommand("idem_key_123", HASH, "INVALID_DESTINATION", NOW, TTL)).block();
        assertThat(adapter.reserve(reserve).block().outcome())
                .isEqualTo(LoginOtpSendReservationOutcome.FAILED_EXISTING);
        ops.clear();
        adapter.reserve(reserve).block();
        adapter.unknown(new LoginOtpSendTerminalCommand("idem_key_123", HASH, "INDETERMINATE", NOW, TTL)).block();
        assertThat(adapter.reserve(reserve).block().outcome())
                .isEqualTo(LoginOtpSendReservationOutcome.UNKNOWN_OUTCOME);
    }

    private RedisLoginOtpSendIdempotencyAdapter adapter(MemoryOps ops) {
        return new RedisLoginOtpSendIdempotencyAdapter(new RedisLoginOtpSendIdempotencyKeyFactory("ngtpa"), ops);
    }

    static final class MemoryOps implements LoginOtpSendRedisOperations {
        final Map<String, String> values = new HashMap<>();
        String lastKey, lastValue;

        public Mono<Boolean> setIfAbsent(String k, String v, Duration t) {
            lastKey = k;
            lastValue = v;
            if (values.containsKey(k))
                return Mono.just(false);
            values.put(k, v);
            return Mono.just(true);
        }

        public Mono<Void> set(String k, String v, Duration t) {
            lastKey = k;
            lastValue = v;
            values.put(k, v);
            return Mono.empty();
        }

        public Mono<Optional<String>> get(String k) {
            return Mono.just(Optional.ofNullable(values.get(k)));
        }

        void clear() {
            values.clear();
        }
    }
}
