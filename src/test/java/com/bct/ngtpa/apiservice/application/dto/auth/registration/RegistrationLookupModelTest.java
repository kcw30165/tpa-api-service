package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationLookupModelTest {

  @Test
  void registrationLookupCommandRequiresValidatedFields() {
    assertThatThrownBy(
            () ->
                new RegistrationLookupCommand(
                    null,
                    "1234567890",
                    "31/01/1980",
                    RegistrationContactType.EMAIL,
                    "member@example.com",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new RegistrationLookupCommand(
                    MemberIdentityType.GPFS,
                    " ",
                    "31/01/1980",
                    RegistrationContactType.EMAIL,
                    "member@example.com",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("registration lookup command fields must not be blank");
    assertThatThrownBy(
            () ->
                new RegistrationLookupCommand(
                    MemberIdentityType.GPFS,
                    "1234567890",
                    " ",
                    RegistrationContactType.EMAIL,
                    "member@example.com",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("registration lookup command fields must not be blank");
    assertThatThrownBy(
            () ->
                new RegistrationLookupCommand(
                    MemberIdentityType.GPFS,
                    "1234567890",
                    "31/01/1980",
                    RegistrationContactType.EMAIL,
                    " ",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("registration lookup command fields must not be blank");

    var command =
        new RegistrationLookupCommand(
            MemberIdentityType.GPFS,
            "1234567890",
            "31/01/1980",
            RegistrationContactType.EMAIL,
            "member@example.com",
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
    assertThat(command.toString())
        .doesNotContain("1234567890", "member@example.com", "31/01/1980", "91234567")
        .contains("GPFS", "EMAIL");
  }

  @Test
  void lookupResultDoesNotExposeAccountsInToString() {
    var account =
        new RegistrationLookupAccount(
            "OI", "JPM", null, null, null, null, null, Boolean.FALSE, null);
    var result =
        new RegistrationLookupResult(RegistrationLookupStatus.ELIGIBLE, List.of(account), null);

    assertThat(account.toString()).doesNotContain("member@example.com", "91234567", "OI", "JPM");
    assertThat(result.toString()).doesNotContain("member@example.com", "91234567", "OI", "JPM");
    assertThat(result.accounts()).containsExactly(account);
    assertThatThrownBy(() -> result.accounts().add(account))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
