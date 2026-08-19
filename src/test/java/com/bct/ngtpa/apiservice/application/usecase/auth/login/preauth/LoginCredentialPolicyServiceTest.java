package com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import org.junit.jupiter.api.Test;

class LoginCredentialPolicyServiceTest {

  private final LoginCredentialPolicyService service =
      new LoginCredentialPolicyService(
          true,
          LoginUsernameNormalization.ASCII_LOWERCASE,
          1,
          120,
          "^[A-Za-z0-9._-]+$",
          false,
          true,
          1,
          128);

  @Test
  void trimsAndAsciiLowercasesUsernameButPreservesPasswordExactly() {
    var normalized =
        service.validateAndNormalize(
            new CredentialAuthenticationCommand(
                "  Member.ABC  ",
                "  Exact Case Password  ",
                "en",
                "req-1",
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));

    assertThat(normalized.username()).isEqualTo("member.abc");
    assertThat(normalized.password()).isEqualTo("  Exact Case Password  ");
  }

  @Test
  void asciiLowercaseNormalizesOnlyConfiguredAsciiUsernameCharacters() {
    var normalized =
        service.validateAndNormalize(
            new CredentialAuthenticationCommand(
                "ABC-Z_9",
                "Exact",
                "en",
                "req-1",
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));
    assertThat(normalized.username()).isEqualTo("abc-z_9");
  }

  @Test
  void rejectsBlankOverLengthOrPatternInvalidCredentials() {
    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    new CredentialAuthenticationCommand(
                        " ",
                        "Exact",
                        "en",
                        "req",
                        com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .isInstanceOf(InvalidLoginCredentialsException.class);
    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    new CredentialAuthenticationCommand(
                        "member/invalid",
                        "Exact",
                        "en",
                        "req",
                        com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .isInstanceOf(InvalidLoginCredentialsException.class);
    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    new CredentialAuthenticationCommand(
                        "member",
                        "",
                        "en",
                        "req",
                        com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .isInstanceOf(InvalidLoginCredentialsException.class);
  }
}
