package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpResendServiceTest {
  static final Instant NOW = Instant.parse("2026-07-24T04:00:00Z");
  static final ResendLoginOtpCommand COMMAND =
      new ResendLoginOtpCommand(
          "afs_secret", "csrf_secret", "idem_key_123", "https://portal", null, "en", "req");

  @Test
  void replacesDeliversThenBindsNewChallenge() {
    var delivered = new AtomicReference<MessageDeliveryCommand>();
    var bound = new AtomicReference<LoginOtpSessionBindingCommand>();
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.just(
                new ReplaceOtpChallengeResult(
                    "old",
                    "new",
                    "123456",
                    OtpReplacementOutcome.REPLACED,
                    NOW.plusSeconds(300),
                    300,
                    60,
                    0,
                    null));
          }
        };
    var service =
        service(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext("member", null, java.util.Map.of(), NOW, "v1"),
                            "old",
                        new LoginPreAuthDestination(
                                "dst_selected", "mobile", "91234567", "****4567"),
                            "csrf_hash")),
            otp,
            c -> {
              delivered.set(c);
              return Mono.just(
                  new MessageDeliveryResult(
                      MessageDeliveryStatus.ACCEPTED, c.channel(), null, null, null));
            },
            c -> {
              bound.set(c);
              return Mono.empty();
            });
    StepVerifier.create(service.resend(COMMAND))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.RESENT);
              assertThat(r.maskedDestination()).isEqualTo("****4567");
            })
        .verifyComplete();
    assertThat(delivered.get().templateVariables()).containsEntry("code", "123456");
    assertThat(bound.get().newOtpChallengeId()).isEqualTo("new");
  }

  @Test
  void cooldownDoesNotDeliverOrBind() {
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.just(
                new ReplaceOtpChallengeResult(
                    "old", null, null, OtpReplacementOutcome.COOLDOWN_ACTIVE, null, 0, 60, 0, 25));
          }
        };
    var service =
        service(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext("member", null, java.util.Map.of(), NOW, "v1"),
                        "old",
                        new LoginPreAuthDestination(
                            "dst_selected", "email", "member@example.com", "m***@example.com"),
                        "csrf_hash")),
            otp,
            c -> Mono.error(new AssertionError()),
            c -> Mono.error(new AssertionError()));
    StepVerifier.create(service.resend(COMMAND))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.COOLDOWN_ACTIVE);
              assertThat(r.retryAfterSeconds()).isEqualTo(25);
            })
        .verifyComplete();
  }

  @Test
  void invalidContextHasNoOtpSideEffects() {
    var service =
        service(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.invalid(
                        LoginProtectedAuthFlowContextStatus.CSRF_INVALID)),
            new Stub() {},
            c -> Mono.error(new AssertionError()),
            c -> Mono.error(new AssertionError()));
    StepVerifier.create(service.resend(COMMAND))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.CSRF_INVALID);
              assertThat(r.maskedDestination()).isNull();
              assertThat(r.expiresInSeconds()).isNull();
              assertThat(r.resendCooldownSeconds()).isNull();
              assertThat(r.retryAfterSeconds()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void missingDefinitionFailsBeforeChallengeIsReplaced() {
    AtomicReference<Boolean> replaceCalled = new AtomicReference<>(false);
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            replaceCalled.set(true);
            return Mono.error(new AssertionError("must not replace"));
          }
        };
    var service =
        new LoginOtpResendService(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext("member", null, java.util.Map.of(), NOW, "v1"),
                        "old",
                        new LoginPreAuthDestination(
                            "dst_selected", "mobile", "91234567", "****4567"),
                        "csrf_hash")),
            otp,
            c -> Mono.error(new AssertionError("must not deliver")),
            c -> Mono.error(new AssertionError("must not bind")),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new LoginOtpSendPolicy(6, true, 300, 60, 5, false, 0),
            (type, channel, locale) -> {
              throw new IllegalStateException("login-otp definition missing");
            });

    StepVerifier.create(service.resend(COMMAND)).verifyError(IllegalStateException.class);
    assertThat(replaceCalled.get()).isFalse();
  }

  @Test
  void resendDeliversSubjectBodyTemplateCodeAndMinuteTtlFromResolvedDefinition() {
    var delivered = new AtomicReference<MessageDeliveryCommand>();
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.just(
                new ReplaceOtpChallengeResult(
                    "old",
                    "new",
                    "654321",
                    OtpReplacementOutcome.REPLACED,
                    NOW.plusSeconds(300),
                    300,
                    60,
                    0,
                    null));
          }
        };
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.EMAIL,
            "en",
            "Registry subject",
            "Registry body {code}",
            null,
            "minutes");
    var service =
        new LoginOtpResendService(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext("member", null, java.util.Map.of(), NOW, "v1"),
                        "old",
                        new LoginPreAuthDestination(
                            "dst_selected", "email", "member@example.com", "m***@example.com"),
                        "csrf_hash")),
            otp,
            c -> {
              delivered.set(c);
              return Mono.just(
                  new MessageDeliveryResult(
                      MessageDeliveryStatus.ACCEPTED, c.channel(), null, null, null));
            },
            c -> Mono.empty(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new LoginOtpSendPolicy(
                6,
                true,
                300,
                60,
                5,
                false,
                0),
            (type, channel, locale) -> definition);

    StepVerifier.create(service.resend(COMMAND))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.RESENT))
        .verifyComplete();

    assertThat(delivered.get().templateCode()).isEqualTo("login-otp");
    assertThat(delivered.get().subject()).isEqualTo("Registry subject");
    assertThat(delivered.get().body()).isEqualTo("Registry body {code}");
    assertThat(delivered.get().templateVariables()).containsEntry("ttlValue", "5");
    assertThat(delivered.get().templateVariables()).containsEntry("ttlUnit", "minutes");
  }

  @Test
  void resendCarriesNonNullTrustCodeAndSchemeNamesIntoTemplateVariables() {
    var delivered = new AtomicReference<MessageDeliveryCommand>();
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.just(
                new ReplaceOtpChallengeResult(
                    "old", "new", "654321", OtpReplacementOutcome.REPLACED,
                    NOW.plusSeconds(300), 300, 60, 0, null));
          }
        };
    var service =
        service(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext(
                            "member",
                            null,
                            java.util.Map.of(),
                            NOW,
                            "v1",
                            "DBA",
                            "Example Scheme",
                            "示例計劃"),
                        "old",
                        new LoginPreAuthDestination(
                            "dst_selected", "mobile", "91234567", "****4567"),
                        "csrf_hash")),
            otp,
            c -> {
              delivered.set(c);
              return Mono.just(
                  new MessageDeliveryResult(
                      MessageDeliveryStatus.ACCEPTED, c.channel(), null, null, null));
            },
            c -> Mono.empty());

    StepVerifier.create(service.resend(COMMAND)).expectNextCount(1).verifyComplete();

    assertThat(delivered.get().templateVariables()).containsEntry("trustCode", "DBA");
    assertThat(delivered.get().templateVariables()).containsEntry("schemeName", "Example Scheme");
    assertThat(delivered.get().templateVariables())
        .containsEntry("schemeNameChi", "示例計劃");
  }

  @Test
  void resendOmitsTrustCodeAndSchemeNameKeysWhenContextCarriesNulls() {
    var delivered = new AtomicReference<MessageDeliveryCommand>();
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.just(
                new ReplaceOtpChallengeResult(
                    "old", "new", "654321", OtpReplacementOutcome.REPLACED,
                    NOW.plusSeconds(300), 300, 60, 0, null));
          }
        };
    var service =
        service(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext("member", null, java.util.Map.of(), NOW, "v1"),
                            "old",
                        new LoginPreAuthDestination(
                                "dst_selected", "mobile", "91234567", "****4567"),
                            "csrf_hash")),
            otp,
            c -> {
              delivered.set(c);
              return Mono.just(
                  new MessageDeliveryResult(
                      MessageDeliveryStatus.ACCEPTED, c.channel(), null, null, null));
            },
            c -> Mono.empty());

    StepVerifier.create(service.resend(COMMAND)).expectNextCount(1).verifyComplete();

    assertThat(delivered.get().templateVariables()).doesNotContainKeys(
        "trustCode", "schemeName", "schemeNameChi");
  }

  @Test
  void resendFallsBackToSchemeNameWhenSchemeNameChiIsBlank() {
    var delivered = new AtomicReference<MessageDeliveryCommand>();
    OtpChallengePort otp =
        new Stub() {
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.just(
                new ReplaceOtpChallengeResult(
                    "old", "new", "654321", OtpReplacementOutcome.REPLACED,
                    NOW.plusSeconds(300), 300, 60, 0, null));
          }
        };
    var service =
        service(
            c ->
                Mono.just(
                    LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext(
                            "member",
                            null,
                            java.util.Map.of(),
                            NOW,
                            "v1",
                            "JPM",
                            "JP MORGAN CHASE - JMCI",
                            ""),
                        "old",
                        new LoginPreAuthDestination(
                            "dst_selected", "mobile", "91234567", "****4567"),
                        "csrf_hash")),
            otp,
            c -> {
              delivered.set(c);
              return Mono.just(
                  new MessageDeliveryResult(
                      MessageDeliveryStatus.ACCEPTED, c.channel(), null, null, null));
            },
            c -> Mono.empty());

    StepVerifier.create(service.resend(COMMAND)).expectNextCount(1).verifyComplete();

    assertThat(delivered.get().templateVariables())
        .containsEntry("schemeNameChi", "JP MORGAN CHASE - JMCI");
  }

  private LoginOtpResendService service(
      LoginProtectedAuthFlowContextLoader l,
      OtpChallengePort o,
      MessageDeliveryPort m,
      LoginOtpSessionBindingPort b) {
    return new LoginOtpResendService(
        l,
        o,
        m,
        b,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new LoginOtpSendPolicy(6, true, 300, 60, 5, false, 0),
        (type, channel, locale) ->
            new NoticeTemplateDefinition(
                type, channel, locale, "Code", "Your code is {code}", null, "minutes"));
  }

  abstract static class Stub implements OtpChallengePort {
    public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) {
      return Mono.empty();
    }
  }
}
