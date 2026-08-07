package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestCommandTest {

    @Test
    void requiresSessionAndSchemeRef() {
        assertThatThrownBy(() -> new UsernameRecoveryRequestCommand(null, new UsernameRecoverySchemeRef("sch_1")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryRequestCommand(new AuthFlowSessionId("afs_1"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void retainsSessionAndSchemeRef() {
        var command = new UsernameRecoveryRequestCommand(
                new AuthFlowSessionId("afs_opaque"), new UsernameRecoverySchemeRef("sch_opaque-1"));

        assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque");
        assertThat(command.schemeRef().value()).isEqualTo("sch_opaque-1");
    }

    @Test
    void toStringDoesNotDiscloseSessionOrSchemeRef() {
        var command = new UsernameRecoveryRequestCommand(
                new AuthFlowSessionId("afs_secret"), new UsernameRecoverySchemeRef("sch_secret"));

        assertThat(command.toString())
                .doesNotContain("afs_secret", "sch_secret")
                .contains("authFlowSessionId=<redacted>", "schemeRef=<redacted>");
    }
}
