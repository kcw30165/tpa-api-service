package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PasswordValidationGrantServiceTest {
  private static final AuthenticatedSessionId ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final AuthenticatedAccountRef ACCOUNT =
      new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx");
  private static final PasswordValidationPurpose PURPOSE =
      PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE;
  private static final PayloadDigest BINDING = new PayloadDigest(1, "b".repeat(64));
  private final AuthenticatedSessionConcurrencyService writer =
      mock(AuthenticatedSessionConcurrencyService.class);
  private final PasswordValidationGrantService grants = new PasswordValidationGrantService(writer);

  @Test
  void invalidInputsAreRejectedWithoutAccessingStorage() {
    assertThat(
            grants
                .issue(null, ACCOUNT, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null)
                .block()
                .status())
        .isEqualTo(PasswordValidationGrantService.Status.REJECTED);
    assertThat(
            grants
                .issue(ID, null, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null)
                .block()
                .token())
        .isNull();
    assertThat(
            grants
                .issue(ID, ACCOUNT, null, BINDING, PasswordCheckResult.CORRECT, null)
                .block()
                .token())
        .isNull();
    assertThat(
            grants
                .issue(ID, ACCOUNT, PURPOSE, null, PasswordCheckResult.CORRECT, null)
                .block()
                .token())
        .isNull();
    for (String token : new String[] {null, "", "bad", "!".repeat(43)}) {
      var consumed = grants.consume(ID, ACCOUNT, PURPOSE, token, null).block();
      assertThat(consumed.status()).isEqualTo(PasswordValidationGrantService.Status.REJECTED);
      assertThat(consumed.reason()).isEqualTo("TOKEN_MALFORMED");
    }
    assertThat(grants.consume(null, ACCOUNT, PURPOSE, "a".repeat(43), null).block().binding())
        .isNull();
    assertThat(grants.consume(ID, null, PURPOSE, "a".repeat(43), null).block().binding()).isNull();
    verifyNoInteractions(writer);
  }

  @Test
  @SuppressWarnings("unchecked")
  void allTechnicalOutcomesAndPublisherFailuresWithholdAuthorizationWithoutRetry() {
    for (var outcome : AuthenticatedSessionConcurrencyOutcome.values()) {
      if (outcome == AuthenticatedSessionConcurrencyOutcome.APPLIED
          || outcome == AuthenticatedSessionConcurrencyOutcome.UNCHANGED
          || outcome == AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND
          || outcome == AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE) continue;
      reset(writer);
      when(writer.updateIfUsableGuarded(
              eq(ID), any(BiPredicate.class), any(BiFunction.class), isNull()))
          .thenReturn(Mono.just(AuthenticatedSessionConcurrencyResult.of(outcome)));
      assertUnavailable(outcome.name());
      verify(writer, times(2)).updateIfUsableGuarded(eq(ID), any(), any(), isNull());
    }
    reset(writer);
    when(writer.updateIfUsableGuarded(eq(ID), any(), any(), isNull())).thenReturn(Mono.empty());
    assertUnavailable("SYSTEM_FAILURE");
    verify(writer, times(2)).updateIfUsableGuarded(eq(ID), any(), any(), isNull());

    reset(writer);
    when(writer.updateIfUsableGuarded(eq(ID), any(), any(), isNull()))
        .thenReturn(Mono.error(new IllegalStateException("synthetic")));
    assertUnavailable("IllegalStateException");
    verify(writer, times(2)).updateIfUsableGuarded(eq(ID), any(), any(), isNull());
  }

  @Test
  void issueDistinguishesLostAuthenticationWithoutChangingConsumeFailureContract() {
    for (var outcome :
        java.util.List.of(
            AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND,
            AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE)) {
      when(writer.updateIfUsableGuarded(eq(ID), any(), any(), isNull()))
          .thenReturn(Mono.just(AuthenticatedSessionConcurrencyResult.of(outcome)));
      var issued =
          grants.issue(ID, ACCOUNT, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null).block();
      assertThat(issued.status().name()).isEqualTo("SESSION_NOT_USABLE");
      assertThat(issued.token()).isNull();
      assertThat(issued.expiresAt()).isNull();
      assertThat(grants.consume(ID, ACCOUNT, PURPOSE, "a".repeat(43), null).block().status())
          .isEqualTo(PasswordValidationGrantService.Status.UNAVAILABLE);
    }
  }

  @Test
  void consumeAssignsTheFirstFailingEligibilityReason() {
    Instant now = Instant.parse("2026-10-28T07:00:00Z");
    String token = "A".repeat(43);
    String digest = tokenDigest(token);

    assertThat(rejected(null, false, token, now).reason()).isEqualTo("ACCOUNT_NOT_IN_SESSION");
    assertThat(rejected("corrupt", true, token, now).reason()).isEqualTo("STATE_UNREADABLE");
    assertThat(rejected(null, true, token, now).reason()).isEqualTo("NO_GRANT");
    assertThat(rejected("consumed", true, token, now).reason()).isEqualTo("ALREADY_CONSUMED");
    assertThat(
            rejected(
                    new PasswordValidationGrant(
                            digest, new PayloadDigest(2, "c".repeat(64)), now, now.plusSeconds(300))
                        .encode(),
                    true,
                    token,
                    now)
                .reason())
        .isEqualTo("VERSION_UNSUPPORTED");
    assertThat(
            rejected(
                    new PasswordValidationGrant(
                            digest, BINDING, now.plusSeconds(1), now.plusSeconds(301))
                        .encode(),
                    true,
                    token,
                    now)
                .reason())
        .isEqualTo("NOT_YET_VALID");
    assertThat(
            rejected(
                    new PasswordValidationGrant(digest, BINDING, now.minusSeconds(300), now)
                        .encode(),
                    true,
                    token,
                    now)
                .reason())
        .isEqualTo("EXPIRED");
    assertThat(
            rejected(
                    new PasswordValidationGrant(
                            tokenDigest("B".repeat(43)), BINDING, now, now.plusSeconds(300))
                        .encode(),
                    true,
                    token,
                    now)
                .reason())
        .isEqualTo("TOKEN_MISMATCH");
  }

  @Test
  void repeatedEligibilityEvaluationReportsTheReasonFromTheLastEvaluation() {
    Instant now = Instant.parse("2026-10-28T07:00:00Z");
    var session = mock(AuthenticatedSession.class);
    when(session.findAccount(ACCOUNT))
        .thenReturn(Optional.of(mock(AuthenticatedSessionAccount.class)), Optional.empty());
    when(session.featureState()).thenReturn(Map.of());
    when(writer.updateIfUsableGuarded(eq(ID), any(), any(), isNull()))
        .thenAnswer(
            invocation -> {
              BiPredicate<AuthenticatedSession, Instant> eligibility = invocation.getArgument(1);
              assertThat(eligibility.test(session, now)).isFalse();
              assertThat(eligibility.test(session, now)).isFalse();
              return Mono.just(AuthenticatedSessionConcurrencyResult.unchanged(session));
            });

    var result = grants.consume(ID, ACCOUNT, PURPOSE, "A".repeat(43), null).block();

    assertThat(result.status()).isEqualTo(PasswordValidationGrantService.Status.REJECTED);
    assertThat(result.reason()).isEqualTo("ACCOUNT_NOT_IN_SESSION");
  }

  @Test
  void legacyTwoArgumentConsumeResultConstructorRemainsAvailable() {
    assertThat(
            new PasswordValidationGrantService.ConsumeResult(
                    PasswordValidationGrantService.Status.REJECTED, null)
                .reason())
        .isNull();
  }

  private PasswordValidationGrantService.ConsumeResult rejected(
      String encoded, boolean accountPresent, String token, Instant now) {
    var session = mock(AuthenticatedSession.class);
    when(session.findAccount(ACCOUNT))
        .thenReturn(
            accountPresent
                ? Optional.of(mock(AuthenticatedSessionAccount.class))
                : Optional.empty());
    when(session.featureState())
        .thenReturn(
            encoded == null
                ? Map.of()
                : Map.of("password-validation:" + ACCOUNT.value() + ":" + PURPOSE.name(), encoded));
    when(writer.updateIfUsableGuarded(eq(ID), any(), any(), isNull()))
        .thenAnswer(
            invocation -> {
              BiPredicate<AuthenticatedSession, Instant> eligibility = invocation.getArgument(1);
              assertThat(eligibility.test(session, now)).isFalse();
              return Mono.just(AuthenticatedSessionConcurrencyResult.unchanged(session));
            });
    return grants.consume(ID, ACCOUNT, PURPOSE, token, null).block();
  }

  private static String tokenDigest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }

  private void assertUnavailable(String reason) {
    var issued =
        grants.issue(ID, ACCOUNT, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null).block();
    assertThat(issued.status()).isEqualTo(PasswordValidationGrantService.Status.UNAVAILABLE);
    assertThat(issued.token()).isNull();
    assertThat(issued.expiresAt()).isNull();
    var consumed = grants.consume(ID, ACCOUNT, PURPOSE, "a".repeat(43), null).block();
    assertThat(consumed.status()).isEqualTo(PasswordValidationGrantService.Status.UNAVAILABLE);
    assertThat(consumed.binding()).isNull();
    assertThat(consumed.reason()).isEqualTo(reason);
  }
}
