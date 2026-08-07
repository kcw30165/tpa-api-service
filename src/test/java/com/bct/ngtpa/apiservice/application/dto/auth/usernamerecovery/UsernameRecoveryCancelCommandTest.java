package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import org.junit.jupiter.api.Test;

class UsernameRecoveryCancelCommandTest {

    @Test
    void requiresSession() {
        assertThatThrownBy(() -> new UsernameRecoveryCancelCommand(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void retainsSession() {
        var command = new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_opaque"));

        assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque");
    }

    @Test
    void toStringDoesNotDiscloseSession() {
        var command = new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_secret"));

        assertThat(command.toString())
                .doesNotContain("afs_secret")
                .contains("authFlowSessionId=<redacted>");
    }
}
