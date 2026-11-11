package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.CancelInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.PasswordCheckPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.*;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class PasswordValidationIssuanceServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-28T07:00:00Z");
  private final PasswordValidationThrottleService throttle =
      mock(PasswordValidationThrottleService.class);
  private final PasswordCheckPort passwords = mock(PasswordCheckPort.class);
  private final PasswordValidationGrantService grants = mock(PasswordValidationGrantService.class);
  private final RevokeAuthenticatedSessionUseCase revocation =
      mock(RevokeAuthenticatedSessionUseCase.class);
  private final FailureEventPort failures = mock(FailureEventPort.class);
  private final PasswordValidationPurposePrecheckPolicyRegistry prechecks =
      new PasswordValidationPurposePrecheckPolicyRegistry(
          List.of(new PersonalInformationPasswordValidationPrecheckPolicy()),
          Set.of(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE));
  private final OperationPayloadBindingPort bindings = new JsonOperationPayloadCanonicalizer();
  private final PasswordValidationIssuanceService service =
      new PasswordValidationIssuanceService(
          throttle,
          passwords,
          grants,
          revocation,
          Clock.fixed(NOW, ZoneOffset.UTC),
          failures,
          bindings,
          prechecks);
  private final PasswordValidationIssuanceCommand command =
      new PasswordValidationIssuanceCommand(
          new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
          new PortalAccessContext(
              new ActorContext("actor-marker", ActorUserRole.ROLE_INPUT),
              new AccountContext(
                  "acc_abcdefghijklmnopqrstuvwx", "TEST", "p", "c", "t", null, null, null)),
          "unchanged-marker",
          PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
          "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false,\"fields\":{\"name\":\"x\"}}",
          new PersonalInformationPasswordValidationPayload(
              PersonalInformationPasswordValidationPayload.JsonKind.OBJECT,
              true,
              PersonalInformationPasswordValidationPayload.JsonKind.STRING,
              "v1",
              true,
              PersonalInformationPasswordValidationPayload.JsonKind.BOOLEAN,
              false,
              true,
              PersonalInformationPasswordValidationPayload.JsonKind.OBJECT,
              List.of("formVersion", "applyToAllAccounts", "fields"),
              List.of("name")),
          "test");

  private void admit(PasswordCheckResult result) {
    when(throttle.check(any(), any()))
        .thenReturn(
            Mono.just(
                new PasswordValidationThrottleService.Decision(
                    PasswordValidationThrottleService.Status.ADMITTED, 0)));
    when(passwords.check(any())).thenReturn(Mono.just(result));
  }

  @Test
  void downstreamUsesSelectedAccountEnvironmentAndPreservesIdentityAndPassword() {
    admit(PasswordCheckResult.INCORRECT);
    assertThat(service.issue(command).block().status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.INCORRECT);
    var capture = org.mockito.ArgumentCaptor.forClass(PasswordCheckCommand.class);
    verify(passwords).check(capture.capture());
    assertThat(capture.getValue().customerId()).isEqualTo(command.context().actor().actorUserId());
    assertThat(capture.getValue().password()).isEqualTo(command.password());
    assertThat(capture.getValue().accountEnv()).isEqualTo("TEST");
  }

  @ParameterizedTest
  @MethodSource("restrictedScenarioIssuanceCases")
  void restrictedCimIssuanceRejectsBeforeThrottlePasswordOrGrant(
      PasswordValidationPurpose purpose,
      String operationPayloadJson,
      InvestmentChoiceScenario scenario,
      String errorCode)
      throws Exception {
    var cache = mock(InvestmentChoiceSnapshotCacheService.class);
    var state =
        InvestmentChoiceSnapshotState.restricted(
            command.context().account().accountRef(),
            command.context().account().policyNo(),
            scenario);
    when(cache.readState(any(), any(), any(), any())).thenReturn(Mono.just(Optional.of(state)));
    var restrictedPrechecks =
        new PasswordValidationPurposePrecheckPolicyRegistry(
            List.of(
                new ChangeInvestmentChoicePasswordValidationPrecheckPolicy(cache, failures),
                new CancelInvestmentChoicePasswordValidationPrecheckPolicy(cache, failures)),
            Set.of(
                PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE,
                PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE));
    var restrictedService =
        new PasswordValidationIssuanceService(
            throttle,
            passwords,
            grants,
            revocation,
            Clock.fixed(NOW, ZoneOffset.UTC),
            failures,
            bindings,
            restrictedPrechecks);
    ObjectMapper json = new ObjectMapper();
    PasswordValidationPurposePayload payload =
        purpose == PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE
            ? new ChangeInvestmentChoicePasswordValidationPayloadDecoder()
                .decode(json.readTree(operationPayloadJson))
            : new CancelInvestmentChoicePasswordValidationPayloadDecoder()
                .decode(json.readTree(operationPayloadJson));
    var issuanceCommand =
        new PasswordValidationIssuanceCommand(
            command.sessionId(),
            command.context(),
            command.password(),
            purpose,
            operationPayloadJson,
            payload,
            command.correlationId());

    var result = restrictedService.issue(issuanceCommand).block();

    assertThat(result.status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.FEATURE_BUSINESS_REJECTED);
    assertThat(result.errorCode()).isEqualTo(errorCode);
    verifyNoInteractions(throttle, passwords, grants, failures);
  }

  static Stream<Arguments> restrictedScenarioIssuanceCases() {
    return Stream.of(
        Arguments.of(
            PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE,
            "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}",
            InvestmentChoiceScenario.FUNCTION_UNAVAILABLE,
            ErrorCodes.INVESTMENT_CHOICE_FUNCTION_UNAVAILABLE),
        Arguments.of(
            PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE,
            "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}",
            InvestmentChoiceScenario.MEMBER_INELIGIBLE,
            ErrorCodes.INVESTMENT_CHOICE_MEMBER_INELIGIBLE),
        Arguments.of(
            PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE,
            "{\"refNo\":\"000000000\"}",
            InvestmentChoiceScenario.FUNCTION_UNAVAILABLE,
            ErrorCodes.INVESTMENT_CHOICE_FUNCTION_UNAVAILABLE),
        Arguments.of(
            PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE,
            "{\"refNo\":\"000000000\"}",
            InvestmentChoiceScenario.MEMBER_INELIGIBLE,
            ErrorCodes.INVESTMENT_CHOICE_MEMBER_INELIGIBLE));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\r\n"})
  void unusableSelectedAccountEnvironmentFailsClosedWithoutDownstream(String accountEnv) {
    admit(PasswordCheckResult.CORRECT);
    var invalid =
        new PasswordValidationIssuanceCommand(
            command.sessionId(),
            new PortalAccessContext(
                command.context().actor(),
                new AccountContext(
                    command.context().account().accountRef(),
                    accountEnv,
                    "p",
                    "c",
                    "t",
                    null,
                    null,
                    null)),
            command.password(),
            command.purpose(),
            command.operationPayloadJson(),
            command.payload(),
            command.correlationId());
    assertThat(service.issue(invalid).block().status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.UNAVAILABLE);
    verifyNoInteractions(passwords, grants, revocation);
  }

  @Test
  void invalidationFailureUsesSharedFailureOwnerWithoutSensitiveCauseOrDimensions() {
    admit(PasswordCheckResult.ACCOUNT_LOCKED);
    when(revocation.revoke(any()))
        .thenReturn(Mono.error(new IllegalStateException("private-marker")));
    assertThat(service.issue(command).block().status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.LOCK_CLEANUP_FAILED);
    var event = org.mockito.ArgumentCaptor.forClass(FailureEvent.class);
    verify(failures).publish(event.capture());
    assertThat(event.getValue().event()).isEqualTo("password-validation.invalidation.failed");
    assertThat(event.getValue().httpStatus()).isEqualTo(503);
    assertThat(event.getValue().safeDimensions())
        .containsEntry("stage", "issuance")
        .containsEntry("reason", "REVOCATION_FAILED");
    assertThat(event.getValue().toString())
        .doesNotContain("private-marker", "actor-marker", "unchanged-marker");
  }

  @Test
  void failedTelemetryCannotHideTheCookieClearingOutcome() {
    admit(PasswordCheckResult.ACCOUNT_LOCKED);
    when(revocation.revoke(any())).thenReturn(Mono.empty());
    doThrow(new IllegalStateException("private-marker")).when(failures).publish(any());
    assertThat(service.issue(command).block().status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.LOCK_CLEANUP_FAILED);
  }

  @Test
  void lockCleanupErrorsEmptyAndUnexpectedActiveResultsRemainExplicitSoCookieCanAlwaysClear() {
    admit(PasswordCheckResult.ACCOUNT_LOCKED);
    for (var status : AuthenticatedSessionLifecycleStatus.values()) {
      if (status == AuthenticatedSessionLifecycleStatus.ACTIVE) continue;
      when(revocation.revoke(any()))
          .thenReturn(Mono.just(AuthenticatedSessionLifecycleResult.of(status)));
      var result = service.issue(command).block();
      assertThat(result.status())
          .isEqualTo(
              status == AuthenticatedSessionLifecycleStatus.FAILED
                  ? PasswordValidationIssuanceResult.Status.LOCK_CLEANUP_FAILED
                  : PasswordValidationIssuanceResult.Status.LOCKED);
      assertThat(result.token()).isNull();
    }
    when(revocation.revoke(any())).thenReturn(Mono.empty());
    assertThat(service.issue(command).block().status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.LOCK_CLEANUP_FAILED);
    when(revocation.revoke(any())).thenThrow(new IllegalStateException("synthetic"));
    assertThat(service.issue(command).block().status())
        .isEqualTo(PasswordValidationIssuanceResult.Status.LOCK_CLEANUP_FAILED);
    verifyNoInteractions(grants);
  }

  @Test
  void expiredByAcknowledgementAndAllNonIssuedGrantResultsWithholdTokenWithoutRetry() {
    admit(PasswordCheckResult.CORRECT);
    for (var status : PasswordValidationGrantService.Status.values()) {
      when(grants.issue(any(), any(), any(), any(), any(), any()))
          .thenReturn(
              Mono.just(
                  new PasswordValidationGrantService.IssueResult(
                      status,
                      status == PasswordValidationGrantService.Status.ISSUED
                          ? "synthetic-token"
                          : null,
                      NOW)));
      var result = service.issue(command).block();
      assertThat(result.status())
          .isEqualTo(
              status == PasswordValidationGrantService.Status.SESSION_NOT_USABLE
                  ? PasswordValidationIssuanceResult.Status.AUTHENTICATION_REQUIRED
                  : PasswordValidationIssuanceResult.Status.UNAVAILABLE);
      assertThat(result.token()).isNull();
    }
    verify(passwords, times(PasswordValidationGrantService.Status.values().length)).check(any());
  }

  @Test
  void remainingLifetimeIsConservativeAndSensitiveApplicationContractsAreRedacted() {
    admit(PasswordCheckResult.CORRECT);
    when(grants.issue(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new PasswordValidationGrantService.IssueResult(
                    PasswordValidationGrantService.Status.ISSUED,
                    "synthetic-token",
                    NOW.plusSeconds(299).plusMillis(900))));
    var result = service.issue(command).block();
    assertThat(result.expiresInSeconds()).isEqualTo(299);
    assertThat(result.toString()).doesNotContain("synthetic-token");
    assertThat(command.toString())
        .doesNotContain("actor-marker", "unchanged-marker", command.operationPayloadJson());
    var order = inOrder(throttle, passwords, grants);
    order.verify(throttle).check(any(), any());
    order.verify(passwords).check(any());
    order.verify(grants).issue(any(), any(), any(), any(), any(), any());
  }

  @Test
  void configuredTtlBoundsReturnedLifetimeAtTheSameValue() {
    admit(PasswordCheckResult.CORRECT);
    var configuredService =
        new PasswordValidationIssuanceService(
            throttle,
            passwords,
            grants,
            revocation,
            Clock.fixed(NOW, ZoneOffset.UTC),
            failures,
            bindings,
            prechecks,
            150);
    when(grants.issue(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new PasswordValidationGrantService.IssueResult(
                    PasswordValidationGrantService.Status.ISSUED,
                    "synthetic-token",
                    NOW.plusSeconds(300))));

    assertThat(configuredService.issue(command).block().expiresInSeconds()).isEqualTo(150);

    when(grants.issue(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new PasswordValidationGrantService.IssueResult(
                    PasswordValidationGrantService.Status.ISSUED,
                    "synthetic-token",
                    NOW.plusSeconds(150))));
    assertThat(configuredService.issue(command).block().expiresInSeconds()).isEqualTo(150);
  }
}
