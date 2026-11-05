package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.NOW;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.RAW_CSRF;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.CSRF_HASH;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.TTL;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.matched;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.unregistered;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * An all-unregistered lookup result is a valid, non-empty lookup and must use the ordinary
 * SESSION_CREATED path (approved requirement 3 and 4).
 */
class UsernameRecoveryStartProtectedOperationAllUnregisteredTest {

  @Test
  void aSingleUnregisteredAccountCreatesTheNormalSessionAndKeepsItsRow() {
    var harness = new UsernameRecoveryStartOperationHarness();
    harness.lookupReturns(matched(unregistered("OI", "JPM")));

    StepVerifier.create(harness.execute())
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
              assertThat(result.authFlowSessionId())
                  .isEqualTo(harness.storedSession().authFlowSessionId());
              assertThat(result.csrfToken()).isEqualTo(RAW_CSRF);
              assertThat(result.expiresInSeconds()).isEqualTo(TTL);
              assertThat(result.schemes())
                  .singleElement()
                  .satisfies(
                      scheme -> {
                        assertThat(scheme.registered()).isFalse();
                        assertThat(scheme.schemeRef()).isNotNull();
                        assertThat(scheme.accountEnv()).isEqualTo("OI");
                        assertThat(scheme.trustCode()).isEqualTo("JPM");
                      });
            })
        .verifyComplete();

    assertThat(harness.calls).containsExactly("captcha", "lookup", "session");
    assertThat(harness.creates.get()).isEqualTo(1);
    assertThat(harness.updates.get()).isZero();

    AuthFlowSession session = harness.storedSession();
    assertThat(session.scenario()).isEqualTo(AuthFlowScenario.USERNAME_RECOVERY);
    assertThat(session.status()).isEqualTo(AuthFlowStatus.STARTED);
    assertThat(session.csrfTokenHash()).isEqualTo(CSRF_HASH);
    assertThat(session.createdAt()).isEqualTo(NOW);
    assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(TTL));

    UsernameRecoveryAuthFlowContext context = harness.binding.load(session);
    assertThat(context.workflowStatus()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
    assertThat(context.candidates())
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.registered()).isFalse();
              assertThat(candidate.userName()).isNull();
              assertThat(candidate.deliveryState())
                  .isEqualTo(UsernameRecoveryDeliveryState.AVAILABLE);
              assertThat(candidate.accountEnv()).isEqualTo("OI");
              assertThat(candidate.trustCode()).isEqualTo("JPM");
            });
  }

  @Test
  void severalUnregisteredAccountsKeepEveryRowWithDistinctOpaqueSchemeRefs() {
    var harness = new UsernameRecoveryStartOperationHarness();
    harness.lookupReturns(
        matched(unregistered("OI", "JPM"), unregistered("DB", "DBA"), unregistered("HK", "HKA")));

    StepVerifier.create(harness.execute())
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
              assertThat(result.schemes())
                  .extracting(UsernameRecoverySchemeProjection::trustCode)
                  .containsExactly("JPM", "DBA", "HKA");
              assertThat(result.schemes())
                  .allSatisfy(scheme -> assertThat(scheme.registered()).isFalse());
              List<UsernameRecoverySchemeRef> refs =
                  result.schemes().stream().map(UsernameRecoverySchemeProjection::schemeRef).toList();
              assertThat(refs).doesNotContainNull().doesNotHaveDuplicates().hasSize(3);
              assertThat(refs).allSatisfy(ref -> assertThat(ref.value()).isNotBlank());
            })
        .verifyComplete();

    UsernameRecoveryAuthFlowContext context = harness.binding.load(harness.storedSession());
    assertThat(context.candidates())
        .extracting(UsernameRecoverySessionCandidate::trustCode)
        .containsExactly("JPM", "DBA", "HKA");
    assertThat(context.candidates())
        .allSatisfy(
            candidate -> {
              assertThat(candidate.registered()).isFalse();
              assertThat(candidate.userName()).isNull();
            });
  }

  @Test
  void sessionPersistenceFailureAfterAnAllUnregisteredLookupReturnsNoSuccess() {
    var harness = new UsernameRecoveryStartOperationHarness();
    harness.lookupReturns(matched(unregistered("OI", "JPM")));
    harness.sessionCreationFails();

    StepVerifier.create(harness.execute())
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(UsernameRecoveryStartException.Reason.SYSTEM_FAILURE);
            })
        .verify();
    assertThat(harness.stored.get()).isNull();
  }
}
