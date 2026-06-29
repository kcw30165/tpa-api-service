package com.bct.ngtpa.apiservice.application.dto.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionCookieInstructionTest {

    @Test
    void createsSetCookieInstruction() {
        var policy = authFlowCookiePolicy();

        var instruction = new SessionCookieInstruction(SessionCookieOperation.SET, policy, "afs_abc");

        assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.SET);
        assertThat(instruction.policy()).isSameAs(policy);
        assertThat(instruction.value()).isEqualTo("afs_abc");
    }

    @Test
    void createsClearCookieInstruction() {
        var policy = authFlowCookiePolicy();

        var instruction = new SessionCookieInstruction(SessionCookieOperation.CLEAR, policy, null);

        assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.CLEAR);
        assertThat(instruction.policy()).isSameAs(policy);
        assertThat(instruction.value()).isNull();
    }

    @Test
    void setCookieInstructionRejectsBlankValue() {
        var policy = authFlowCookiePolicy();

        assertThatThrownBy(() -> new SessionCookieInstruction(SessionCookieOperation.SET, policy, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie value is required for SET operation")
                .hasMessageNotContaining("afs_");
    }

    @Test
    void cookieInstructionToStringDoesNotExposeValue() {
        var instruction = new SessionCookieInstruction(
                SessionCookieOperation.SET,
                authFlowCookiePolicy(),
                "afs_secret_value");

        assertThat(instruction.toString())
                .doesNotContain("afs_secret_value")
                .contains("value=<redacted>");
    }

    @Test
    void clearCookieInstructionNormalizesBlankValueToNull() {
        var instruction = new SessionCookieInstruction(SessionCookieOperation.CLEAR, authFlowCookiePolicy(), " ");

        assertThat(instruction.value()).isNull();
        assertThat(instruction.toString()).contains("value=<empty>");
    }

    private static SessionCookiePolicy authFlowCookiePolicy() {
        return new SessionCookiePolicy(
                "auth_flow_sid",
                "/api/v1/auth",
                1800,
                true,
                true,
                SessionCookieSameSite.NONE);
    }
}
