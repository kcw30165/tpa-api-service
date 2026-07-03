package com.bct.ngtpa.apiservice.application.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import org.junit.jupiter.api.Test;

class SecureSessionIdGeneratorTest {

    private final SecureSessionIdGenerator generator = new SecureSessionIdGenerator();

    @Test
    void generatesOpaqueSessionIdUsingSuppliedPrefix() {
        var id = generator.generate(new SessionIdGenerationCommand("afs", 16));

        assertThat(id.value()).startsWith("afs_");
        assertThat(id.value()).isNotBlank();
        assertThat(id.value()).doesNotContainPattern("\\s");
        assertThat(id.value()).doesNotContain("@", "+852", "91234567");
        assertThat(id.value()).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void supportsDifferentSafePrefixes() {
        var authFlowId = generator.generate(new SessionIdGenerationCommand("afs", 16));
        var loginId = generator.generate(new SessionIdGenerationCommand("login", 16));

        assertThat(authFlowId.value()).startsWith("afs_");
        assertThat(loginId.value()).startsWith("login_");
    }

    @Test
    void generatesDifferentSessionIds() {
        var command = new SessionIdGenerationCommand("afs", 16);

        var first = generator.generate(command);
        var second = generator.generate(command);

        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void rejectsUnsafeSessionIdPrefix() {
        for (String prefix : new String[] {"", " ", "auth flow", "afs:", "afs/email", "member@example.com"}) {
            assertThatThrownBy(() -> generator.generate(new SessionIdGenerationCommand(prefix, 16)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Session id prefix is invalid");
        }
    }

    @Test
    void rejectsInsufficientEntropy() {
        assertThatThrownBy(() -> generator.generate(new SessionIdGenerationCommand("afs", 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Session id entropy must be at least 16 bytes");
    }

    @Test
    void sessionIdDoesNotExposeCookieNameOrScenario() {
        var id = generator.generate(new SessionIdGenerationCommand("afs", 16));

        assertThat(id.value())
                .doesNotContain("auth_flow_sid")
                .doesNotContain("REGISTRATION")
                .doesNotContain("LOGIN")
                .doesNotContain("FORGOT_PASSWORD")
                .doesNotContain("USERNAME_RECOVERY");
    }
}
