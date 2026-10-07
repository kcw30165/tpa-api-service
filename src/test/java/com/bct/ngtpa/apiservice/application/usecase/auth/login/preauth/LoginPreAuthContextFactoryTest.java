package com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.LoginCredentialDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.bct.ngtpa.apiservice.application.port.out.LoginDestinationIdGenerator;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoginPreAuthContextFactoryTest {

  @Test
  void createsOpaqueDestinationMapAndConvertsMobileToPublicSms() {
    AtomicInteger sequence = new AtomicInteger();
    LoginDestinationIdGenerator generator = () -> "dst_opaque_value_" + sequence.incrementAndGet();
    var factory =
        new LoginPreAuthContextFactory(generator, new MaskingService(new MaskingProperties()));
    var result =
        new CredentialAuthenticationResult(
            CredentialAuthenticationStatus.AUTHENTICATED,
            "member",
            "role_user",
            0,
            List.of(
                new LoginCredentialDestination("email", "member@example.com"),
                new LoginCredentialDestination("mobile", "91234567")),
            null);
    var context = factory.create(result, Instant.parse("2026-07-20T00:00:00Z"));
    assertThat(context.normalizedUsername()).isEqualTo("member");
    assertThat(context.destinationsById()).hasSize(2);
    assertThat(context.destinationsById().values())
        .extracting(LoginPreAuthDestination::channel)
        .containsExactly("email", "sms");
    assertThat(context.destinationsById().values())
        .extracting(LoginPreAuthDestination::maskedDestination)
        .containsExactly("m****r@example.com", "9******7");
    assertThat(context.toString())
        .doesNotContain("member", "member@example.com", "91234567", "dst_opaque_value");
  }

  @Test
  void carriesTrustCodeAndSchemeNamesFromResultIntoContext() {
    var factory =
        new LoginPreAuthContextFactory(
            () -> "dst_opaque_value", new MaskingService(new MaskingProperties()));
    var result =
        new CredentialAuthenticationResult(
            CredentialAuthenticationStatus.AUTHENTICATED,
            "member",
            "role_user",
            0,
            List.of(new LoginCredentialDestination("email", "member@example.com")),
            null,
            "DBA",
            "Example Scheme",
            "示例計劃");

    var context = factory.create(result, Instant.parse("2026-07-20T00:00:00Z"));

    assertThat(context.trustCode()).isEqualTo("DBA");
    assertThat(context.schemeName()).isEqualTo("Example Scheme");
    assertThat(context.schemeNameChi()).isEqualTo("示例計劃");
  }

  @Test
  void rejectsNonAuthenticatedOrDestinationlessResults() {
    var factory =
        new LoginPreAuthContextFactory(
            () -> "dst_opaque_value", new MaskingService(new MaskingProperties()));
    assertThatThrownBy(
            () ->
                factory.create(
                    new CredentialAuthenticationResult(
                        CredentialAuthenticationStatus.INCORRECT_PASSWORD,
                        null,
                        null,
                        1,
                        List.of(),
                        null),
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                factory.create(
                    new CredentialAuthenticationResult(
                        CredentialAuthenticationStatus.AUTHENTICATED,
                        "member",
                        null,
                        0,
                        List.of(),
                        null),
                    Instant.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("destination");
  }
}
