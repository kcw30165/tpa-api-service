package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class UsernameRecoveryStartCommandTest {

    private static final Locale LANGUAGE = Locale.forLanguageTag("zh-HK");
    private static final String IDEMPOTENCY_KEY = "idem-start-123";

    @Test
    void requiresValidatedFields() {
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                null, "1234567890", "04/07/1978", "member@example.com", null, "cap_1", LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, null, "04/07/1978", "member@example.com", null, "cap_1", LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", null, "member@example.com", null, "cap_1", LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, null, LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, "cap_1", null, IDEMPOTENCY_KEY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, "cap_1", LANGUAGE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankIdentityValueDateOfBirthCaptchaChallengeIdAndIdempotencyKey() {
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "  ", "04/07/1978", "member@example.com", null, "cap_1", LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "   ", "member@example.com", null, "cap_1", LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, "  ", LANGUAGE, IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
        assertThatThrownBy(() -> new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "member@example.com", null, "cap_1", LANGUAGE, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery start command fields must not be blank");
    }

    @Test
    void trimsRequiredFieldsKeepsContactsAsProvidedAndPreservesIdempotencyKeyCase() {
        var command = new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "  1234567890  ", "  04/07/1978  ", "member@example.com", null, "  cap_1  ",
                Locale.forLanguageTag("zh-HK"), "  IdEm_StArT_123  ");

        assertThat(command.identityType()).isEqualTo(MemberIdentityType.STAFFNO);
        assertThat(command.identityValue()).isEqualTo("1234567890");
        assertThat(command.dateOfBirth()).isEqualTo("04/07/1978");
        assertThat(command.email()).isEqualTo("member@example.com");
        assertThat(command.mobile()).isNull();
        assertThat(command.captchaChallengeId()).isEqualTo("cap_1");
        assertThat(command.language()).isEqualTo(Locale.forLanguageTag("zh-HK"));
        assertThat(command.idempotencyKey()).isEqualTo("IdEm_StArT_123");
    }

    @Test
    void retainsNormalizedLanguageWithoutDefaultingToEnglish() {
        var command = new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", null, null, "cap_1",
                Locale.forLanguageTag("en"), "idem-start-456");

        assertThat(command.language()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void allowsEitherOrBothContactsForUseCaseValidation() {
        // Exactly-one email/mobile is a use-case validation rule (UR-START-005); the data
        // contract accepts any combination and never rejects on it.
        assertThat(new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", null, null, "cap_1", LANGUAGE, IDEMPOTENCY_KEY).email())
                .isNull();
        assertThat(new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "1234567890", "04/07/1978", "a@b.c", "66290597", "cap_1", LANGUAGE, IDEMPOTENCY_KEY).mobile())
                .isEqualTo("66290597");
    }

    @Test
    void toStringDoesNotExposeSensitiveValuesOrIdempotencyKey() {
        var command = new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "3030609", "04/07/1978",
                "HULLCS.QGLYBCY@UC.KJI", "66290597", "cap_secret",
                Locale.forLanguageTag("zh-HK"), "idem_secret_key_123");

        assertThat(command.toString())
                .doesNotContain("3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", "66290597", "cap_secret",
                        "idem_secret_key_123")
                .contains("STAFFNO", "language=", "idempotencyKey=");
        assertThat(command.toString()).doesNotContain("zh_HK", "zh-HK");
    }
}
