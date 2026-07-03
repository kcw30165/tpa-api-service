package com.bct.ngtpa.apiservice.application.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionTtlCalculatorTest {

    private final SessionTtlCalculator calculator = new SessionTtlCalculator();

    @Test
    void ttlCalculatorComputesExpiresAt() {
        var now = Instant.parse("2026-06-29T00:00:00Z");
        var policy = new SessionTtlPolicy(true, true, 1800);

        assertThat(calculator.expiresAt(now, policy))
                .isEqualTo(Instant.parse("2026-06-29T00:30:00Z"));
    }

    @Test
    void ttlCalculatorRejectsInvalidTtl() {
        var now = Instant.parse("2026-06-29T00:00:00Z");

        assertThatThrownBy(() -> calculator.expiresAt(now, new SessionTtlPolicy(true, true, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enabled session TTL must be greater than zero");
        assertThatThrownBy(() -> calculator.expiresAt(now, new SessionTtlPolicy(true, true, -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enabled session TTL must be greater than zero");
    }

    @Test
    void ttlCalculatorRejectsNullInputs() {
        var policy = new SessionTtlPolicy(true, true, 1800);

        assertThatThrownBy(() -> calculator.expiresAt(null, policy))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.expiresAt(Instant.parse("2026-06-29T00:00:00Z"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sessionTtlPolicyCanRepresentScenarioPolicy() {
        var registration = new SessionTtlPolicy(true, true, 1800);
        var usernameRecovery = new SessionTtlPolicy(true, false, 900);

        assertThat(registration.enabled()).isTrue();
        assertThat(registration.required()).isTrue();
        assertThat(registration.ttlSeconds()).isEqualTo(1800);

        assertThat(usernameRecovery.enabled()).isTrue();
        assertThat(usernameRecovery.required()).isFalse();
        assertThat(usernameRecovery.ttlSeconds()).isEqualTo(900);
    }
}
