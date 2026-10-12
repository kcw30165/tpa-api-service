package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class LoginOtpDestinationResolverTest {
  private final LoginOtpDestinationResolver resolver = new LoginOtpDestinationResolver();

  @Test
  void resolvesOnlyOpaqueDestinationIdFromProtectedContext() {
    var destinations = new LinkedHashMap<String, LoginPreAuthDestination>();
    destinations.put(
        "dst_first_opaque",
        new LoginPreAuthDestination(
            "dst_first_opaque", "email", "member@example.com", "m*****@example.com"));
    destinations.put(
        "dst_second_opaque",
        new LoginPreAuthDestination("dst_second_opaque", "sms", "91234567", "****4567"));
    var context =
        new LoginPreAuthContext(
            "member01", null, destinations, Instant.parse("2026-07-24T00:00:00Z"), "v1", null, null, null);

    var resolved =
        resolver.resolve(
            context,
            new SendLoginOtpCommand(
                "afs_opaque_session",
                "csrf_opaque",
                "dst_second_opaque",
                "idem_opaque",
                "https://portal.example",
                null,
                "en",
                "req-1"));

    assertThat(resolved.destinationId()).isEqualTo("dst_second_opaque");
    assertThat(resolved.channel()).isEqualTo(OtpChannel.SMS);
    assertThat(resolved.destination()).isEqualTo("91234567");
    assertThat(resolved.maskedDestination()).isEqualTo("****4567");
    assertThat(resolved.toString())
        .doesNotContain("91234567", "****4567", "afs_opaque_session", "csrf_opaque");
  }

  @Test
  void rejectsUnknownDestinationWithoutFallbackToSingleOrFirstEntry() {
    var context =
        new LoginPreAuthContext(
            "member01",
            null,
            java.util.Map.of(
                "dst_only",
                new LoginPreAuthDestination(
                    "dst_only", "email", "member@example.com", "m*****@example.com")),
            Instant.now(),
            "v1",
            null,
            null,
            null);
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    context,
                    new SendLoginOtpCommand(
                        "afs_opaque_session",
                        "csrf_opaque",
                        "dst_unknown",
                        "idem_opaque",
                        null,
                        null,
                        "en",
                        "req-1")))
        .isInstanceOf(InvalidLoginOtpDestinationException.class);
  }

  @Test
  void mapsApimMobileVocabularyToPublicSmsChannel() {
    var context =
        new LoginPreAuthContext(
            "member01",
            null,
            java.util.Map.of(
                "dst_mobile",
                new LoginPreAuthDestination("dst_mobile", "mobile", "91234567", "****4567")),
            Instant.now(),
            "v1",
            null,
            null,
            null);
    var resolved =
        resolver.resolve(
            context,
            new SendLoginOtpCommand(
                "afs_opaque_session",
                "csrf_opaque",
                "dst_mobile",
                "idem_opaque",
                null,
                null,
                "en",
                "req-1"));
    assertThat(resolved.channel()).isEqualTo(OtpChannel.SMS);
  }
}
