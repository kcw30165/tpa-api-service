package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionTimeoutPolicyTest {
    @Test
    void loginSessionHasSeparateSlidingAbsoluteAndTerminalPolicies() {
        var session = new AuthWorkflowProperties.LoginSession();
        session.setIdleTimeoutSeconds(900);
        session.setAbsoluteTimeoutSeconds(28800);
        session.setTerminalRetentionSeconds(300);

        assertThat(session.getIdleTimeoutSeconds()).isEqualTo(900);
        assertThat(session.getAbsoluteTimeoutSeconds()).isEqualTo(28800);
        assertThat(session.getTerminalRetentionSeconds()).isEqualTo(300);
    }

    @Test
    void yamlUsesExplicitAuthenticatedSessionPolicyNames() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml)
                .contains("AUTH_WORKFLOWS_LOGIN_SESSION_ABSOLUTE_TIMEOUT_SECONDS")
                .contains("AUTH_WORKFLOWS_LOGIN_SESSION_IDLE_TIMEOUT_SECONDS")
                .contains("AUTH_WORKFLOWS_LOGIN_SESSION_TERMINAL_RETENTION_SECONDS")
                .doesNotContain("AUTH_WORKFLOWS_LOGIN_SESSION_TTL_SECONDS");
    }

    @Test
    void slidingExpiryRemainsCappedByAbsoluteExpiry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionLifecycleService.java"));
        assertThat(source)
                .contains("min(now.plus(idleTimeout), session.absoluteExpiresAt())")
                .contains("Duration.between(now, idle)")
                .contains("now.plus(absoluteTtl)");
    }
}
