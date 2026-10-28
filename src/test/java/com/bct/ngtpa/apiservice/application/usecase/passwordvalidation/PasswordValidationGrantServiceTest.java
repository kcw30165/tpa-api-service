package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PasswordValidationGrantServiceTest {
  private static final AuthenticatedSessionId ID = new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final AuthenticatedAccountRef ACCOUNT = new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx");
  private static final PasswordValidationPurpose PURPOSE = PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE;
  private static final PayloadDigest BINDING = new PayloadDigest(1, "b".repeat(64));
  private final AuthenticatedSessionConcurrencyService writer = mock(AuthenticatedSessionConcurrencyService.class);
  private final PasswordValidationGrantService grants = new PasswordValidationGrantService(writer);

  @Test
  void invalidInputsAreRejectedWithoutAccessingStorage() {
    assertThat(grants.issue(null, ACCOUNT, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null).block().status())
        .isEqualTo(PasswordValidationGrantService.Status.REJECTED);
    assertThat(grants.issue(ID, null, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null).block().token()).isNull();
    assertThat(grants.issue(ID, ACCOUNT, null, BINDING, PasswordCheckResult.CORRECT, null).block().token()).isNull();
    assertThat(grants.issue(ID, ACCOUNT, PURPOSE, null, PasswordCheckResult.CORRECT, null).block().token()).isNull();
    for (String token : new String[]{null, "", "bad", "!".repeat(43)}) {
      assertThat(grants.consume(ID, ACCOUNT, PURPOSE, token, null).block().status())
          .isEqualTo(PasswordValidationGrantService.Status.REJECTED);
    }
    assertThat(grants.consume(null, ACCOUNT, PURPOSE, "a".repeat(43), null).block().binding()).isNull();
    assertThat(grants.consume(ID, null, PURPOSE, "a".repeat(43), null).block().binding()).isNull();
    verifyNoInteractions(writer);
  }

  @Test
  @SuppressWarnings("unchecked")
  void allTechnicalOutcomesAndPublisherFailuresWithholdAuthorizationWithoutRetry() {
    for (var outcome : AuthenticatedSessionConcurrencyOutcome.values()) {
      if (outcome == AuthenticatedSessionConcurrencyOutcome.APPLIED ||
          outcome == AuthenticatedSessionConcurrencyOutcome.UNCHANGED) continue;
      reset(writer);
      when(writer.updateIfUsableGuarded(eq(ID), any(BiPredicate.class), any(BiFunction.class), isNull()))
          .thenReturn(Mono.just(AuthenticatedSessionConcurrencyResult.of(outcome)));
      assertUnavailable();
      verify(writer, times(2)).updateIfUsableGuarded(eq(ID), any(), any(), isNull());
    }
    for (Mono<AuthenticatedSessionConcurrencyResult> failure : java.util.List.of(
        Mono.<AuthenticatedSessionConcurrencyResult>empty(),
        Mono.<AuthenticatedSessionConcurrencyResult>error(new IllegalStateException("synthetic")))) {
      reset(writer);
      when(writer.updateIfUsableGuarded(eq(ID), any(), any(), isNull())).thenReturn(failure);
      assertUnavailable();
      verify(writer, times(2)).updateIfUsableGuarded(eq(ID), any(), any(), isNull());
    }
  }

  private void assertUnavailable() {
    var issued = grants.issue(ID, ACCOUNT, PURPOSE, BINDING, PasswordCheckResult.CORRECT, null).block();
    assertThat(issued.status()).isEqualTo(PasswordValidationGrantService.Status.UNAVAILABLE);
    assertThat(issued.token()).isNull();
    assertThat(issued.expiresAt()).isNull();
    var consumed = grants.consume(ID, ACCOUNT, PURPOSE, "a".repeat(43), null).block();
    assertThat(consumed.status()).isEqualTo(PasswordValidationGrantService.Status.UNAVAILABLE);
    assertThat(consumed.binding()).isNull();
  }
}
