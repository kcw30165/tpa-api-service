package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Username Recovery effective destination result.
 *
 * <p>Verifies the resolved effective destination and channel invariants and that
 * {@code toString()} never discloses the destination value.
 */
class UsernameRecoveryEffectiveDestinationResultTest {

    @Test
    void carriesEffectiveDestinationAndChannel() {
        var result = new UsernameRecoveryEffectiveDestinationResult(
                "override@example.test", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

        assertThat(result.destination()).isEqualTo("override@example.test");
        assertThat(result.channel()).isEqualTo(UsernameRecoveryVerifiedDestination.Channel.EMAIL);
    }

    @Test
    void rejectsNullDestination() {
        assertThatThrownBy(() -> new UsernameRecoveryEffectiveDestinationResult(
                null, UsernameRecoveryVerifiedDestination.Channel.EMAIL))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void rejectsBlankDestination() {
        assertThatThrownBy(() -> new UsernameRecoveryEffectiveDestinationResult(
                "   ", UsernameRecoveryVerifiedDestination.Channel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination must not be blank");
    }

    @Test
    void rejectsNullChannel() {
        assertThatThrownBy(() -> new UsernameRecoveryEffectiveDestinationResult(
                "member@example.test", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel");
    }

    @Test
    void toStringNeverDisclosesDestinationValue() {
        var result = new UsernameRecoveryEffectiveDestinationResult(
                "member@example.test", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

        assertThat(result.toString())
                .contains("<redacted>")
                .contains("EMAIL")
                .doesNotContain("member@example.test");
    }
}
