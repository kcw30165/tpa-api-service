package com.bct.ngtpa.apiservice.application.service.security.csrf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256CsrfTokenHasherTest {

    private final CsrfTokenHasher hasher = new Sha256CsrfTokenHasher();

    @Test
    void hashDoesNotEqualRawToken() {
        String rawToken = "csrf_sample_token";

        String hash = hasher.hash(rawToken);

        assertThat(hash).isNotBlank();
        assertThat(hash).isNotEqualTo(rawToken);
        assertThat(hash).doesNotContain(rawToken);
    }

    @Test
    void hashIsStableForSameToken() {
        String rawToken = "csrf_sample_token";

        assertThat(hasher.hash(rawToken)).isEqualTo(hasher.hash(rawToken));
    }

    @Test
    void matchesReturnsTrueForCorrectToken() {
        String rawToken = "csrf_sample_token";
        String storedHash = hasher.hash(rawToken);

        assertThat(hasher.matches(rawToken, storedHash)).isTrue();
    }

    @Test
    void matchesReturnsFalseForIncorrectOrBlankInputs() {
        String storedHash = hasher.hash("csrf_sample_token");

        assertThat(hasher.matches("csrf_wrong_token", storedHash)).isFalse();
        assertThat(hasher.matches(" ", storedHash)).isFalse();
        assertThat(hasher.matches(null, storedHash)).isFalse();
        assertThat(hasher.matches("csrf_sample_token", " ")).isFalse();
        assertThat(hasher.matches("csrf_sample_token", null)).isFalse();
    }
}
