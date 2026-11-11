package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ChangeInvestmentChoicePasswordValidationPrecheckPolicyTest {
  private static final String VALID_BODY =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
          + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
          + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}";
  private static final String SESSION_ID_VALUE = "sid_abcdefghijklmnopqrstuvwx";
  private static final String ACCOUNT_REF = "snapshot-account-reference";
  private static final String POLICY_NO = "snapshot-policy-number";
  private static final String REQUEST_ID = "request-safe";
  private static final String EVENT = "investment-choice.submission.snapshot.missing";
  private static final String SENSITIVE_SOURCE = "source-sensitive";
  private static final String SENSITIVE_FUND = "fund-sensitive";
  private static final String SENSITIVE_POLICY_ERROR = "snapshot-error-sensitive";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId(SESSION_ID_VALUE);
  private static final PasswordValidationPrecheckContext CONTEXT =
      new PasswordValidationPrecheckContext(
          SESSION_ID,
          new PortalAccessContext(
              new ActorContext("actor-sensitive", ActorUserRole.ROLE_USER_MBR),
              new AccountContext(
                  ACCOUNT_REF, "TEST", POLICY_NO, "certificate-sensitive", null, null, null, null)),
          REQUEST_ID);

  private final InvestmentChoiceSnapshotCacheService snapshotCache =
      org.mockito.Mockito.mock(InvestmentChoiceSnapshotCacheService.class);
  private final FailureEventPort failures = org.mockito.Mockito.mock(FailureEventPort.class);
  private final ChangeInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new ChangeInvestmentChoicePasswordValidationPayloadDecoder();
  private final ChangeInvestmentChoicePasswordValidationPrecheckPolicy policy =
      new ChangeInvestmentChoicePasswordValidationPrecheckPolicy(snapshotCache, failures);
  private final Logger logger =
      (Logger)
          LoggerFactory.getLogger(ChangeInvestmentChoicePasswordValidationPrecheckPolicy.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachLogger() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachLogger() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidVRuleBodies")
  void structurallySafeVRuleRejectionsReadSnapshotOnceWithoutPublishingEvents(
      String caseName, String body, List<PasswordValidationPrecheckViolation> expected)
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.of(availableSnapshot(1))));

    var result = precheck(body);

    assertThat(result.isSuccess()).as(caseName).isFalse();
    assertThat(result.violations()).as(caseName).containsExactlyElementsOf(expected);
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
    assertNoSensitiveLogValues();
  }

  @Test
  void structuralV1FailureDoesNotReadSnapshotOrPublishEvents() throws JsonProcessingException {
    var result = precheck("{\"switchSources\":\"BO\",\"allocations\":[]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FORM,
                ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID));
    verifyNoInteractions(snapshotCache, failures);
    assertNoSensitiveLogValues();
  }

  @ParameterizedTest
  @ValueSource(strings = {"FUNCTION_UNAVAILABLE", "MEMBER_INELIGIBLE"})
  void restrictedScenarioRejectsValidSubmitWithItsBusinessCodeWithoutPublishingEvents(
      String scenario) throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.restricted(
                        ACCOUNT_REF, POLICY_NO, InvestmentChoiceScenario.valueOf(scenario)))));

    var result = precheck(VALID_BODY);

    assertThat(result.isBusinessRejected()).isTrue();
    assertThat(result.businessErrorCode())
        .isEqualTo(
            scenario.equals("FUNCTION_UNAVAILABLE")
                ? ErrorCodes.INVESTMENT_CHOICE_FUNCTION_UNAVAILABLE
                : ErrorCodes.INVESTMENT_CHOICE_MEMBER_INELIGIBLE);
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
    assertNoSensitiveLogValues();
  }

  @Test
  void restrictedSubmitReturnsVViolationsWithoutEvaluatingNormalSnapshotRules()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.restricted(
                        ACCOUNT_REF, POLICY_NO, InvestmentChoiceScenario.FUNCTION_UNAVAILABLE))));

    var result =
        precheck(
            "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":39}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void preservesCombinedVRuleOrderingAndSuppressesTotalAfterInvalidPercentages()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.of(availableSnapshot(1))));

    var result =
        precheck(
            """
            {"switchSources":["BO","BO"," "],"allocations":[
              {"fundCode":"DBLF","percentage":40},
              {"fundCode":"DBLF","percentage":"60"},
              {"percentage":0}]}
            """);

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                "switchSources"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                "switchSources[1]"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[2].fundCode"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                "allocations[1].fundCode"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[1].percentage"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[2].percentage"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void aggregatesTotalViolationBeforeUnavailableSource() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshot(
                        1,
                        List.of(new InvestmentChoiceSnapshot.SwitchSource("XX", false)),
                        List.of(
                            new InvestmentChoiceSnapshot.Fund("DBLF", true),
                            new InvestmentChoiceSnapshot.Fund("DHKM", true))))));

    var result =
        precheck(
            "{\"switchSources\":[\"XX\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":30}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[0]"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void aggregatesTotalViolationBeforeUnavailableFund() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshot(
                        1,
                        List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", true)),
                        List.of(
                            new InvestmentChoiceSnapshot.Fund("DBLF", false),
                            new InvestmentChoiceSnapshot.Fund("DHKM", true))))));

    var result =
        precheck(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":30}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[0].fundCode"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void invalidPercentageSkipsItsIntervalCheckButOtherValidPercentageAndSourceAreChecked()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshot(
                        25,
                        List.of(new InvestmentChoiceSnapshot.SwitchSource("XX", false)),
                        List.of(
                            new InvestmentChoiceSnapshot.Fund("DBLF", true),
                            new InvestmentChoiceSnapshot.Fund("DHKM", true))))));

    var result =
        precheck(
            "{\"switchSources\":[\"XX\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":\"60\"},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[0].percentage"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[1].percentage"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[0]"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void blankAndMissingOperandsAreSkippedWithoutReindexingReadableOperands()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshot(
                        1,
                        List.of(new InvestmentChoiceSnapshot.SwitchSource("XX", false)),
                        List.of(new InvestmentChoiceSnapshot.Fund("FUND_X", false))))));

    var result =
        precheck(
            "{\"switchSources\":[\" \",\"XX\"],\"allocations\":["
                + "{\"percentage\":50},"
                + "{\"fundCode\":\"FUND_X\",\"percentage\":50}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                "switchSources"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[0].fundCode"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[1]"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[1].fundCode"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void blankFundIsSkippedWhileLaterReadableFundIsChecked() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshot(
                        1,
                        List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", true)),
                        List.of(new InvestmentChoiceSnapshot.Fund("FUND_X", false))))));

    var result =
        precheck(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\" \",\"percentage\":50},"
                + "{\"fundCode\":\"FUND_X\",\"percentage\":50}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[0].fundCode"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[1].fundCode"));
    assertThat(result.violations()).doesNotHaveDuplicates();
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void missingSourceArrayDoesNotCreateSourceSnapshotViolation() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshot(
                        1, List.of(), List.of(new InvestmentChoiceSnapshot.Fund("DBLF", false))))));

    var result = precheck("{\"allocations\":[{\"fundCode\":\"DBLF\",\"percentage\":100}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                "switchSources"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[0].fundCode"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void knownVViolationsWinOverSnapshotMissWithoutPublishingIncident()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));

    var result =
        precheck(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":30}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void knownVViolationsWinOverSnapshotReadFailureWithoutPublishingIncident()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.error(new IllegalStateException(SENSITIVE_POLICY_ERROR)));

    var result =
        precheck(
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":30}]}");

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.CROSS_FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                "allocations"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void validVRulesReadSnapshotExactlyOnceWithSessionAccountPolicyAndRequestContext()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.of(availableSnapshot())));

    var result = precheck(VALID_BODY);

    assertThat(result.isSuccess()).isTrue();
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void reusesSnapshotValidatorForS1ToS3InRuleAndIndexOrder() throws JsonProcessingException {
    var unavailableSnapshot =
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF,
            POLICY_NO,
            25,
            List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", false)),
            List.of(new InvestmentChoiceSnapshot.Fund("DBLF", false)),
            List.of());
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(Optional.of(InvestmentChoiceSnapshotState.normal(unavailableSnapshot))));

    var result = precheck(VALID_BODY);

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[0].percentage"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[1].percentage"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[0]"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[1]"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[0].fundCode"),
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[1].fundCode"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
  }

  @Test
  void snapshotMissPublishesExactlyOneSafeFeatureEventAndReturnsUnavailable()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));

    StepVerifier.create(precheckMono(VALID_BODY))
        .expectError(InvestmentChoiceSubmissionUnavailableException.class)
        .verify();

    var expectedEvent =
        new FailureEvent(
            EVENT,
            REQUEST_ID,
            "INVESTMENT_CHOICE",
            "SNAPSHOT",
            FailureOrigin.LOCAL_SYSTEM,
            "SNAPSHOT_MISSING",
            ErrorCodes.INVESTMENT_CHOICE_SUBMISSION_UNAVAILABLE,
            503,
            false,
            Map.of("reason", "missing"));
    var event = org.mockito.ArgumentCaptor.forClass(FailureEvent.class);
    verify(failures, times(1)).publish(event.capture());
    assertThat(event.getValue()).isEqualTo(expectedEvent);
    assertThat(event.getValue().toString())
        .doesNotContain(ACCOUNT_REF, POLICY_NO, SESSION_ID_VALUE, SENSITIVE_SOURCE, SENSITIVE_FUND);
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  @Test
  void snapshotReadFailureIsAnUnavailableMissWithoutLeakingTheCause()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.error(new IllegalStateException(SENSITIVE_POLICY_ERROR)));

    var error = new AtomicReference<Throwable>();
    StepVerifier.create(precheckMono(VALID_BODY)).expectErrorSatisfies(error::set).verify();

    assertThat(error.get()).isInstanceOf(InvestmentChoiceSubmissionUnavailableException.class);
    assertThat(error.get().toString()).doesNotContain(SENSITIVE_POLICY_ERROR);
    verify(failures, times(1)).publish(any(FailureEvent.class));
    assertNoSensitiveLogValues();
  }

  @Test
  void telemetryFailureDoesNotChangeSnapshotUnavailableOutcome() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));
    org.mockito.Mockito.doThrow(new IllegalStateException(SENSITIVE_POLICY_ERROR))
        .when(failures)
        .publish(any(FailureEvent.class));

    StepVerifier.create(precheckMono(VALID_BODY))
        .expectError(InvestmentChoiceSubmissionUnavailableException.class)
        .verify();

    verify(failures, times(1)).publish(any(FailureEvent.class));
    assertNoSensitiveLogValues();
  }

  private PasswordValidationPrecheckResult precheck(String body) throws JsonProcessingException {
    var result = new AtomicReference<PasswordValidationPrecheckResult>();
    StepVerifier.create(precheckMono(body)).assertNext(result::set).verifyComplete();
    return result.get();
  }

  private Mono<PasswordValidationPrecheckResult> precheckMono(String body)
      throws JsonProcessingException {
    ChangeInvestmentChoicePasswordValidationPayload payload = decoder.decode(JSON.readTree(body));
    return policy.precheck(payload, CONTEXT);
  }

  private static InvestmentChoiceSnapshotState availableSnapshot() {
    return availableSnapshot(20);
  }

  private static InvestmentChoiceSnapshotState availableSnapshot(int intervalPcnt) {
    return InvestmentChoiceSnapshotState.normal(
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF,
            POLICY_NO,
            intervalPcnt,
            List.of(
                new InvestmentChoiceSnapshot.SwitchSource("BO", true),
                new InvestmentChoiceSnapshot.SwitchSource("EE", true)),
            List.of(
                new InvestmentChoiceSnapshot.Fund("DBLF", true),
                new InvestmentChoiceSnapshot.Fund("DHKM", true)),
            List.of()));
  }

  private static InvestmentChoiceSnapshotState snapshot(
      int intervalPcnt,
      List<InvestmentChoiceSnapshot.SwitchSource> sources,
      List<InvestmentChoiceSnapshot.Fund> funds) {
    return InvestmentChoiceSnapshotState.normal(
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF, POLICY_NO, intervalPcnt, sources, funds, List.of()));
  }

  private static Stream<Arguments> invalidVRuleBodies() {
    return Stream.of(
        Arguments.of(
            "V2 missing switch source",
            "{\"allocations\":[" + VALID_ALLOCATION + "]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                    "switchSources"))),
        Arguments.of(
            "V3 duplicate switch source",
            "{\"switchSources\":[\"BO\",\"BO\"],\"allocations\":[" + VALID_ALLOCATION + "]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                    "switchSources[1]"))),
        Arguments.of(
            "V4 empty allocations",
            "{\"switchSources\":[\"BO\"],\"allocations\":[]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
                    "allocations"))),
        Arguments.of(
            "V5 missing fund",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"percentage\":100}]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                    "allocations[0].fundCode"))),
        Arguments.of(
            "V6 duplicate fund",
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":50},"
                + "{\"fundCode\":\"DBLF\",\"percentage\":50}]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                    "allocations[1].fundCode"))),
        Arguments.of(
            "V7 invalid percentage",
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60.0},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":40}]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                    "allocations[0].percentage"))),
        Arguments.of(
            "V8 invalid total",
            "{\"switchSources\":[\"BO\"],\"allocations\":["
                + "{\"fundCode\":\"DBLF\",\"percentage\":60},"
                + "{\"fundCode\":\"DHKM\",\"percentage\":39}]}",
            List.of(
                precheckViolation(
                    PasswordValidationPrecheckViolation.Kind.CROSS_FIELD,
                    ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                    "allocations"))));
  }

  private static final String VALID_ALLOCATION = "{\"fundCode\":\"DBLF\",\"percentage\":100}";

  private static PasswordValidationPrecheckViolation precheckViolation(
      PasswordValidationPrecheckViolation.Kind kind, String code, String... targets) {
    return new PasswordValidationPrecheckViolation(kind, code, List.of(targets));
  }

  private void assertNoSensitiveLogValues() {
    String messages =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + right);
    assertThat(messages)
        .doesNotContain(
            ACCOUNT_REF,
            POLICY_NO,
            SESSION_ID_VALUE,
            "certificate-sensitive",
            "actor-sensitive",
            SENSITIVE_SOURCE,
            SENSITIVE_FUND,
            SENSITIVE_POLICY_ERROR,
            "60",
            "40");
  }
}
