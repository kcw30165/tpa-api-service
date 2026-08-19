package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import org.junit.jupiter.api.Test;

class UsernameRecoveryLookupCommandTest {

  @Test
  void requiresValidatedFields() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupCommand(
                    null,
                    "1234567890",
                    "04/07/1978",
                    "member@example.com",
                    null,
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupCommand(
                    MemberIdentityType.STAFFNO,
                    null,
                    "04/07/1978",
                    "member@example.com",
                    null,
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupCommand(
                    MemberIdentityType.STAFFNO,
                    "1234567890",
                    null,
                    "member@example.com",
                    null,
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsBlankIdentityValueAndDateOfBirth() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupCommand(
                    MemberIdentityType.STAFFNO,
                    "  ",
                    "04/07/1978",
                    "member@example.com",
                    null,
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("username recovery lookup command fields must not be blank");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupCommand(
                    MemberIdentityType.STAFFNO,
                    "1234567890",
                    " ",
                    "member@example.com",
                    null,
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("username recovery lookup command fields must not be blank");
  }

  @Test
  void trimsIdentityAndDateOfBirthAndKeepsContactsAsProvided() {
    var command =
        new UsernameRecoveryLookupCommand(
            MemberIdentityType.STAFFNO,
            "  1234567890  ",
            "  04/07/1978  ",
            "member@example.com",
            null,
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

    assertThat(command.identityType()).isEqualTo(MemberIdentityType.STAFFNO);
    assertThat(command.identityValue()).isEqualTo("1234567890");
    assertThat(command.dateOfBirth()).isEqualTo("04/07/1978");
    assertThat(command.email()).isEqualTo("member@example.com");
    assertThat(command.mobile()).isNull();
  }

  @Test
  void allowsEitherOrBothContactsForUseCaseValidation() {
    assertThat(
            new UsernameRecoveryLookupCommand(
                    MemberIdentityType.STAFFNO,
                    "1234567890",
                    "04/07/1978",
                    null,
                    null,
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)
                .email())
        .isNull();
    assertThat(
            new UsernameRecoveryLookupCommand(
                    MemberIdentityType.STAFFNO,
                    "1234567890",
                    "04/07/1978",
                    "a@b.c",
                    "66290597",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)
                .mobile())
        .isEqualTo("66290597");
  }

  @Test
  void toStringDoesNotExposeSensitiveValues() {
    var command =
        new UsernameRecoveryLookupCommand(
            MemberIdentityType.STAFFNO,
            "3011825",
            "15/07/1969",
            "member@example.com",
            "66290597",
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

    assertThat(command.toString())
        .doesNotContain("3011825", "15/07/1969", "member@example.com", "66290597")
        .contains("STAFFNO");
  }
}
