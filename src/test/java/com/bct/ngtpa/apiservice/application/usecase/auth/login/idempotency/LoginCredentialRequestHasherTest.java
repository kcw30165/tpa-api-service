package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import org.junit.jupiter.api.Test;

class LoginCredentialRequestHasherTest {
    private final LoginCredentialRequestHasher hasher = new LoginCredentialRequestHasher();

    @Test
    void hashIsStableButDoesNotExposeUsernameOrPassword() {
        var command = new CredentialAuthenticationCommand("member01", "Exact Password", "en", "req-1");
        var first = hasher.hash(command);
        var second = hasher.hash(command);

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).matches("^[a-f0-9]{64}$");
        assertThat(first.toString()).doesNotContain(first.value(), "member01", "Exact Password");
    }

    @Test
    void exactPasswordAndNormalizedUsernameBothAffectHash() {
        var base = hasher.hash(new CredentialAuthenticationCommand("member01", "Exact", "en", "req"));
        assertThat(hasher.hash(new CredentialAuthenticationCommand("member02", "Exact", "en", "req")))
                .isNotEqualTo(base);
        assertThat(hasher.hash(new CredentialAuthenticationCommand("member01", "exact", "en", "req")))
                .isNotEqualTo(base);
    }
}
