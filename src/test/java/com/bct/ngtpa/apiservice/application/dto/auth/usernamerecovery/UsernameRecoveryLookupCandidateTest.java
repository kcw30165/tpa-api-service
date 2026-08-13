package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameRecoveryLookupCandidateTest {

    @Test
    void registeredCandidateRetainsUsername() {
        var candidate = new UsernameRecoveryLookupCandidate("DB", "DBA", true, "john.doe");

        assertThat(candidate.accountEnv()).isEqualTo("DB");
        assertThat(candidate.trustCode()).isEqualTo("DBA");
        assertThat(candidate.registered()).isTrue();
        assertThat(candidate.userName()).isEqualTo("john.doe");
    }

    @Test
    void unregisteredCandidateRetainsNoUsername() {
        var candidate = new UsernameRecoveryLookupCandidate("DB", "DBB", false, null);

        assertThat(candidate.registered()).isFalse();
        assertThat(candidate.userName()).isNull();
    }

    @Test
    void rejectsMissingOrBlankAccountEnvAndTrustCode() {
        assertThatThrownBy(() -> new UsernameRecoveryLookupCandidate(null, "DBA", true, "john.doe"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryLookupCandidate(" ", "DBA", true, "john.doe"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsernameRecoveryLookupCandidate("DB", " ", true, "john.doe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registeredCandidateRequiresNonblankUsername() {
        assertThatThrownBy(() -> new UsernameRecoveryLookupCandidate("DB", "DBA", true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registered candidate requires a nonblank userName");
        assertThatThrownBy(() -> new UsernameRecoveryLookupCandidate("DB", "DBA", true, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registered candidate requires a nonblank userName");
    }

    @Test
    void unregisteredCandidateMustNotRetainUsername() {
        assertThatThrownBy(() -> new UsernameRecoveryLookupCandidate("DB", "DBB", false, "john.doe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unregistered candidate must not retain a userName");
    }

    @Test
    void toStringDoesNotDiscloseUsername() {
        var candidate = new UsernameRecoveryLookupCandidate("DB", "DBA", true, "secret.user");

        assertThat(candidate.toString())
                .doesNotContain("secret.user")
                .contains("accountEnv=DB", "trustCode=DBA", "registered=true");
    }
}
