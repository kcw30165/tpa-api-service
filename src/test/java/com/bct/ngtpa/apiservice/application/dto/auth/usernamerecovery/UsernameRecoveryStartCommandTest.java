package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import org.junit.jupiter.api.Test;

class UsernameRecoveryStartCommandTest {

    @Test
    void requiresValidatedFields() {
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                null, "1234567890", "04/07/1978", "member@example.com", null, "cap_1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, null, "04/07/1978", "member@example.com", null, "cap_1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", null, "member@example.com", null, "cap_1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankIdentityValueDateOfBirthAndCaptchaChallengeId() {
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "  ", "04/07/1978", "member@example.com", null, "cap_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "   ", "member@example.com", null, "cap_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
    }

    @Test
    void trimsRequiredFieldsAndKeepsContactsAsProvided() {
        var command = new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "  1234567890  ", "  04/07/1978  ", "member@example.com", null, "  cap_1  ");

        assertThat(command.identityType()).isEqualTo(MemberIdentityType.STAFFNO);
        assertThat(command.identityValue()).isEqualTo("1234567890");
        assertThat(command.dateOfBirth()).isEqualTo("04/07/1978");
        assertThat(command.email()).isEqualTo("member@example.com");
        assertThat(command.mobile()).isNull();
        assertThat(command.captchaChallengeId()).isEqualTo("cap_1");
    }

    @Test
    void allowsEitherOrBothContactsForUseCaseValidation() {
        // Exactly-one email/mobile is a use-case validation rule (UR-START-005); the data
        // contract accepts any combination and never rejects on it.
        assertThat(new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", null, null, "cap_1").email()).isNull();
        assertThat(new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "a@b.c", "66290597", "cap_1").mobile())
                .isEqualTo("66290597");
    }

    @Test
    void toStringDoesNotExposeSensitiveValues() {
        var command = new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "3030609", "04/07/1978",
                "HULLCS.QGLYBCY@UC.KJI", "66290597", "cap_secret");

        assertThat(command.toString())
                .doesNotContain("3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", "66290597", "cap_secret")
                .contains("STAFFNO");
    }
}
