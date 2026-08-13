package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 020 effective-destination masking helper.
 *
 * <p>The mask represents the effective delivery destination for FE display only: email keeps the first
 * and last local-part characters around {@code ****} (API contract example {@code p****c@ah.ozk});
 * mobile keeps only the last four digits (shared Login/Forgot Password convention). The raw value never
 * appears in the output.
 */
class UsernameRecoveryRequestDestinationMaskerTest {

    private final UsernameRecoveryRequestDestinationMasker masker =
            new UsernameRecoveryRequestDestinationMasker();

    @Test
    void masksEmailWithFirstAndLastLocalPartCharacters() {
        assertThat(masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, "pepperc@ah.ozk"))
                .isEqualTo("p****c@ah.ozk");
    }

    @Test
    void masksEmailExampleMatchesTheApiContractShape() {
        assertThat(masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, "pepperc@ah.ozk"))
                .matches("p\\*\\*\\*\\*c@ah\\.ozk");
    }

    @Test
    void masksShortEmailLocalPartSafely() {
        assertThat(masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, "a@b.com"))
                .isEqualTo("a****@b.com");
    }

    @Test
    void masksMobileKeepingLastFourDigits() {
        assertThat(masker.mask(UsernameRecoveryVerifiedDestination.Channel.MOBILE, "91234567"))
                .isEqualTo("****4567");
    }

    @Test
    void maskedOutputNeverContainsTheRawDestination() {
        String masked = masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, "pepperc@ah.ozk");

        assertThat(masked).doesNotContain("pepperc");
    }

    @Test
    void rejectsEmailWithoutAtSign() {
        assertThatThrownBy(() ->
                masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, "no-at-sign"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMobileShorterThanFourDigits() {
        assertThatThrownBy(() ->
                masker.mask(UsernameRecoveryVerifiedDestination.Channel.MOBILE, "123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullChannel() {
        assertThatThrownBy(() ->
                masker.mask(null, "pepperc@ah.ozk"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullDestination() {
        assertThatThrownBy(() ->
                masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankDestination() {
        assertThatThrownBy(() ->
                masker.mask(UsernameRecoveryVerifiedDestination.Channel.EMAIL, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
