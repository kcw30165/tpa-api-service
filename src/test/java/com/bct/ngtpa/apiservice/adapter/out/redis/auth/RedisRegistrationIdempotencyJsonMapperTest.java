package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisRegistrationIdempotencyJsonMapperTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");
    private final RedisRegistrationIdempotencyJsonMapper mapper = new RedisRegistrationIdempotencyJsonMapper();

    @Test
    void jsonMapperRoundTripsCompletedRecord() {
        var record = completedRecord();

        var json = mapper.toJson(record);
        var roundTrip = mapper.fromJson(json);

        assertThat(roundTrip.action()).isEqualTo(record.action());
        assertThat(roundTrip.idempotencyKey()).isEqualTo(record.idempotencyKey());
        assertThat(roundTrip.requestHash()).isEqualTo(record.requestHash());
        assertThat(roundTrip.status()).isEqualTo(RegistrationIdempotencyStatus.COMPLETED);
        assertThat(roundTrip.responseSnapshot()).isNotNull();
        assertThat(roundTrip.responseSnapshot().safeHeaders()).containsExactly(Map.entry("X-Safe", "value"));
    }

    @Test
    void storedJsonDoesNotContainForbiddenRawFieldNames() {
        var json = mapper.toJson(completedRecord());

        assertThat(json).contains("requestHash", "responseSnapshot");
        assertThat(json.toLowerCase())
                .doesNotContain("rawrequestbody")
                .doesNotContain("otpcode")
                .doesNotContain("rawotp")
                .doesNotContain("password")
                .doesNotContain("confirmpassword")
                .doesNotContain("mobile")
                .doesNotContain("email")
                .doesNotContain("hkid")
                .doesNotContain("passport")
                .doesNotContain("policyno")
                .doesNotContain("certno")
                .doesNotContain("auth_flow_sid")
                .doesNotContain("csrftoken")
                .doesNotContain("cookie")
                .doesNotContain("authorization");
    }

    @Test
    void responseSnapshotHeadersRejectUnsafeHeaderPersistence() {
        assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of("Set-Cookie", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe idempotency snapshot header");
        assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of("Cookie", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe idempotency snapshot header");
        assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of("Authorization", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe idempotency snapshot header");
        assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of("X-XSRF-TOKEN", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe idempotency snapshot header");
        assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of("XSRF-TOKEN", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe idempotency snapshot header");
    }

    @Test
    void malformedRedisJsonFailsSafely() {
        assertThatThrownBy(() -> mapper.fromJson("{not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to deserialize registration idempotency record")
                .hasMessageNotContaining("{not-json");
    }

    private static RegistrationIdempotencyRecord completedRecord() {
        return new RegistrationIdempotencyRecord(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123"),
                new RegistrationRequestHash(HASH),
                RegistrationIdempotencyStatus.COMPLETED,
                new RegistrationIdempotencyResponseSnapshot("200", "{\"ok\":true}", Map.of("X-Safe", "value")),
                NOW,
                NOW.plusSeconds(1),
                NOW.plusSeconds(300));
    }
}
