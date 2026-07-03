package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import org.junit.jupiter.api.Test;

class RedisRegistrationIdempotencyKeyFactoryTest {

    @Test
    void buildsRegistrationStartIdempotencyKey() {
        var key = new RedisRegistrationIdempotencyKeyFactory("ngtpa")
                .keyFor(RegistrationIdempotencyAction.REGISTRATION_START, new RegistrationIdempotencyKey("idem_abc123"));

        assertThat(key).isEqualTo("ngtpa:auth:idempotency:registration-start:idem_abc123");
        assertThat(key)
                .doesNotContain("auth_flow_sid")
                .doesNotContain("XSRF-TOKEN")
                .doesNotContain("csrf_")
                .doesNotContain("otp_")
                .doesNotContain("member@example.com")
                .doesNotContain("91234567")
                .doesNotContain("password")
                .doesNotContain("policy")
                .doesNotContain("cert");
    }

    @Test
    void buildsAllActionNamespaces() {
        var factory = new RedisRegistrationIdempotencyKeyFactory("ngtpa");
        var idempotencyKey = new RegistrationIdempotencyKey("idem_abc123");

        assertThat(factory.keyFor(RegistrationIdempotencyAction.REGISTRATION_START, idempotencyKey))
                .isEqualTo("ngtpa:auth:idempotency:registration-start:idem_abc123");
        assertThat(factory.keyFor(RegistrationIdempotencyAction.OTP_RESEND, idempotencyKey))
                .isEqualTo("ngtpa:auth:idempotency:otp-resend:idem_abc123");
        assertThat(factory.keyFor(RegistrationIdempotencyAction.OTP_VERIFY, idempotencyKey))
                .isEqualTo("ngtpa:auth:idempotency:otp-verify:idem_abc123");
        assertThat(factory.keyFor(RegistrationIdempotencyAction.REGISTRATION_COMPLETE, idempotencyKey))
                .isEqualTo("ngtpa:auth:idempotency:registration-complete:idem_abc123");
    }
}
