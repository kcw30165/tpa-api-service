package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Kind;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Validation;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 019 start validation boundary (Registration-compatible conventions):
 * identity-value validation per shared {@link MemberIdentityType}, strict {@code dd/MM/yyyy} DOB,
 * exactly-one email/mobile contact, and email/HK-mobile format, with typed target+kind errors and a
 * normalized verified destination for the protected session.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4 (validation
 * bullet list, UR-START-002..006) and {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 4 (identity-value
 * validation reuses Registration behavior).
 */
class UsernameRecoveryStartValidatorTest {

  private static final String CAPTCHA = "cap_f67f65980e944119b8b81c74d9a1834a";
  private static final String IDEM_KEY = "idem_12345678";
  private static final Locale LANGUAGE = Locale.ENGLISH;

  private final UsernameRecoveryStartValidator validator = new UsernameRecoveryStartValidator();

  @Test
  void acceptsStaffNoWithEmailAndDerivesEmailDestination() {
    Validation validation =
        validator.validate(
            command("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null));

    assertThat(validation.valid()).isTrue();
    assertThat(validation.errors()).isEmpty();
    assertThat(validation.email()).isEqualTo("hullcs.qglybcy@uc.kji");
    assertThat(validation.mobile()).isEmpty();
    assertThat(validation.destination())
        .isEqualTo(
            new UsernameRecoveryVerifiedDestination(
                "hullcs.qglybcy@uc.kji", UsernameRecoveryVerifiedDestination.Channel.EMAIL));
  }

  @Test
  void acceptsGpnWithMobileAndDerivesMobileDestination() {
    Validation validation =
        validator.validate(command("GPN", "12345678", "04/07/1978", null, "66290597"));

    assertThat(validation.valid()).isTrue();
    assertThat(validation.destination())
        .isEqualTo(
            new UsernameRecoveryVerifiedDestination(
                "66290597", UsernameRecoveryVerifiedDestination.Channel.MOBILE));
  }

  @Test
  void rejectsBothContacts() {
    Validation validation =
        validator.validate(
            command("STAFFNO", "3030609", "04/07/1978", "hullcs.qglybcy@uc.kji", "66290597"));

    assertThat(validation.valid()).isFalse();
    assertThat(validation.errors())
        .extracting(UsernameRecoveryStartValidator.Error::target)
        .containsExactlyInAnyOrder("email", "mobile");
    assertThat(validation.errors())
        .allSatisfy(error -> assertThat(error.kind()).isEqualTo(Kind.CONTACT_EXACTLY_ONE_REQUIRED));
  }

  @Test
  void rejectsMissingBothContacts() {
    Validation validation =
        validator.validate(command("STAFFNO", "3030609", "04/07/1978", null, null));

    assertThat(validation.valid()).isFalse();
    assertThat(validation.errors())
        .extracting(UsernameRecoveryStartValidator.Error::kind)
        .containsExactlyInAnyOrder(Kind.CONTACT_REQUIRED, Kind.CONTACT_REQUIRED);
  }

  @Test
  void rejectsMalformedEmailAndMobile() {
    Validation validation =
        validator.validate(command("STAFFNO", "3030609", "04/07/1978", "not-an-email", null));

    assertThat(validation.valid()).isFalse();
    assertThat(validation.errors())
        .containsExactly(new UsernameRecoveryStartValidator.Error("email", Kind.EMAIL_INVALID));

    Validation mobile =
        validator.validate(command("STAFFNO", "3030609", "04/07/1978", null, "12345678"));
    assertThat(mobile.errors())
        .containsExactly(new UsernameRecoveryStartValidator.Error("mobile", Kind.MOBILE_INVALID));
  }

  @Test
  void rejectsInvalidIdentityValuePerType() {
    Validation staffNo =
        validator.validate(
            command("STAFFNO", "30 30609", "04/07/1978", "hullcs.qglybcy@uc.kji", null));
    assertThat(staffNo.errors())
        .containsExactly(
            new UsernameRecoveryStartValidator.Error("identityValue", Kind.IDENTITY_VALUE_INVALID));

    Validation gpn =
        validator.validate(command("GPN", "123", "04/07/1978", "hullcs.qglybcy@uc.kji", null));
    assertThat(gpn.errors())
        .containsExactly(
            new UsernameRecoveryStartValidator.Error("identityValue", Kind.IDENTITY_VALUE_INVALID));
  }

  @Test
  void rejectsInvalidDobWithStrictDayMonthYear() {
    Validation nonStrict =
        validator.validate(
            command("STAFFNO", "3030609", "1978-07-04", "hullcs.qglybcy@uc.kji", null));
    assertThat(nonStrict.errors())
        .containsExactly(
            new UsernameRecoveryStartValidator.Error("dateOfBirth", Kind.DATE_OF_BIRTH_INVALID));

    Validation impossible =
        validator.validate(
            command("STAFFNO", "3030609", "31/02/1978", "hullcs.qglybcy@uc.kji", null));
    assertThat(impossible.valid()).isFalse();
  }

  @Test
  void accumulatesMultipleErrors() {
    Validation validation =
        validator.validate(command("GPN", "bad value", "not-a-date", "bad-email", "12345678"));

    assertThat(validation.valid()).isFalse();
    assertThat(validation.errors())
        .extracting(UsernameRecoveryStartValidator.Error::kind)
        .contains(
            Kind.IDENTITY_VALUE_INVALID,
            Kind.DATE_OF_BIRTH_INVALID,
            Kind.CONTACT_EXACTLY_ONE_REQUIRED);
  }

  @Test
  void invalidValidationCarriesNoDestination() {
    Validation validation =
        validator.validate(command("STAFFNO", "3030609", "04/07/1978", null, null));

    assertThat(validation.destination()).isNull();
  }

  @Test
  void rejectsNullCommand() {
    assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(NullPointerException.class);
  }

  private static UsernameRecoveryStartCommand command(
      String identityType, String identityValue, String dateOfBirth, String email, String mobile) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.valueOf(identityType),
        identityValue,
        dateOfBirth,
        email,
        mobile,
        CAPTCHA,
        LANGUAGE,
        IDEM_KEY);
  }
}
