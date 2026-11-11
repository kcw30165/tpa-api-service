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
import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.CancelInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

class CancelInvestmentChoicePasswordValidationPrecheckPolicyTest {

  private static final String REFERENCE_NO = " 000123 ";
  private static final String INVALID_BODY_ERROR = "invalid-body-cause-marker";
  private static final String SNAPSHOT_ERROR = "snapshot-error-marker";
  private static final String SESSION_ID_VALUE = "sid_abcdefghijklmnopqrstuvwx";
  private static final String ACCOUNT_REF = "account-sensitive-marker";
  private static final String POLICY_NO = "policy-sensitive-marker";
  private static final String REQUEST_ID = "request-safe";
  private static final String SNAPSHOT_MISSING_EVENT = "investment-choice.cancel.snapshot.missing";
  private static final String VALID_BODY = "{\"referenceNo\":\"" + REFERENCE_NO + "\"}";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId(SESSION_ID_VALUE);
  private static final PasswordValidationPrecheckContext CONTEXT =
      new PasswordValidationPrecheckContext(
          SESSION_ID,
          new PortalAccessContext(
              new ActorContext("actor-sensitive-marker", ActorUserRole.ROLE_USER_MBR),
              new AccountContext(
                  ACCOUNT_REF,
                  "TEST",
                  POLICY_NO,
                  "certificate-sensitive-marker",
                  null,
                  null,
                  null,
                  null)),
          REQUEST_ID);

  private final InvestmentChoiceSnapshotCacheService snapshotCache =
      org.mockito.Mockito.mock(InvestmentChoiceSnapshotCacheService.class);
  private final FailureEventPort failures = org.mockito.Mockito.mock(FailureEventPort.class);
  private final CancelInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new CancelInvestmentChoicePasswordValidationPayloadDecoder();
  private final CancelInvestmentChoicePasswordValidationPrecheckPolicy policy =
      new CancelInvestmentChoicePasswordValidationPrecheckPolicy(snapshotCache, failures);
  private final Logger logger =
      (Logger)
          LoggerFactory.getLogger(CancelInvestmentChoicePasswordValidationPrecheckPolicy.class);
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
  @MethodSource("invalidBodies")
  void cv1AndCv2RejectionsDoNotReadSnapshotOrPublishEvents(
      String caseName, String body, List<PasswordValidationPrecheckViolation> expected)
      throws JsonProcessingException {
    var result = precheck(body);

    assertThat(result.isSuccess()).as(caseName).isFalse();
    assertThat(result.violations()).as(caseName).containsExactlyElementsOf(expected);
    verifyNoInteractions(snapshotCache, failures);
    assertNoSensitiveLogValues();
  }

  @Test
  void validReferenceReadsOwnedSnapshotOnceAndPreservesExactReference()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.of(snapshot(List.of(REFERENCE_NO)))));

    var result = precheck(VALID_BODY);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.violations()).isEmpty();
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
    assertNoSensitiveLogValues();
  }

  @Test
  void referenceMissingFromSnapshotReturnsDetailedViolationWithoutIncident()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.of(snapshot(List.of()))));

    var result = precheck(VALID_BODY);

    assertThat(result.violations())
        .containsExactly(
            precheckViolation(
                PasswordValidationPrecheckViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_INVALID,
                "referenceNo"));
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verifyNoInteractions(failures);
    assertNoSensitiveLogValues();
  }

  @ParameterizedTest
  @ValueSource(strings = {"FUNCTION_UNAVAILABLE", "MEMBER_INELIGIBLE"})
  void restrictedScenarioRejectsValidCancelBeforePendingMembership(String scenario)
      throws JsonProcessingException {
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
  void snapshotMissPublishesExactlyOneSafeEventAndReturnsUnavailable()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));

    StepVerifier.create(precheckMono(VALID_BODY))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    assertSnapshotMissEvent();
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  @Test
  void snapshotReadFailureIsUnavailableAndPublishesExactlyOneSafeEvent()
      throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.error(new IllegalStateException(SNAPSHOT_ERROR)));

    var error = new AtomicReference<Throwable>();
    StepVerifier.create(precheckMono(VALID_BODY)).expectErrorSatisfies(error::set).verify();

    assertThat(error.get()).isInstanceOf(InvestmentChoiceCancelUnavailableException.class);
    assertThat(error.get().toString()).doesNotContain(SNAPSHOT_ERROR, REFERENCE_NO);
    assertSnapshotMissEvent();
    verify(snapshotCache, times(1)).readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  @Test
  void telemetryFailureDoesNotReplaceSnapshotUnavailableOutcome() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));
    org.mockito.Mockito.doThrow(new IllegalStateException(SNAPSHOT_ERROR))
        .when(failures)
        .publish(any(FailureEvent.class));

    StepVerifier.create(precheckMono(VALID_BODY))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    verify(failures, times(1)).publish(any(FailureEvent.class));
    assertNoSensitiveLogValues();
  }

  @Test
  void resultAndEventStringsDoNotExposeSubmittedOrContextValues() throws JsonProcessingException {
    when(snapshotCache.readState(SESSION_ID, ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));

    var error = new AtomicReference<Throwable>();
    StepVerifier.create(precheckMono(VALID_BODY)).expectErrorSatisfies(error::set).verify();

    assertThat(error.get()).isInstanceOf(InvestmentChoiceCancelUnavailableException.class);
    assertSnapshotMissEvent();
    assertNoSensitiveLogValues();
  }

  private PasswordValidationPrecheckResult precheck(String body) throws JsonProcessingException {
    var result = new AtomicReference<PasswordValidationPrecheckResult>();
    StepVerifier.create(precheckMono(body)).assertNext(result::set).verifyComplete();
    return result.get();
  }

  private Mono<PasswordValidationPrecheckResult> precheckMono(String body)
      throws JsonProcessingException {
    JsonNode operationPayload = body == null ? null : JSON.readTree(body);
    CancelInvestmentChoicePasswordValidationPayload payload = decoder.decode(operationPayload);
    return policy.precheck(payload, CONTEXT);
  }

  private void assertSnapshotMissEvent() {
    var eventCaptor = org.mockito.ArgumentCaptor.forClass(FailureEvent.class);
    verify(failures, times(1)).publish(eventCaptor.capture());
    FailureEvent event = eventCaptor.getValue();

    assertThat(event)
        .isEqualTo(
            new FailureEvent(
                SNAPSHOT_MISSING_EVENT,
                REQUEST_ID,
                "INVESTMENT_CHOICE",
                "SNAPSHOT",
                FailureOrigin.LOCAL_SYSTEM,
                "SNAPSHOT_MISSING",
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_UNAVAILABLE,
                503,
                false,
                Map.of("reason", "missing")));
    assertThat(event.toString())
        .doesNotContain(REFERENCE_NO, ACCOUNT_REF, POLICY_NO, SESSION_ID_VALUE, SNAPSHOT_ERROR);
  }

  private void assertNoSensitiveLogValues() {
    String messages =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + right);
    assertThat(messages)
        .doesNotContain(
            REFERENCE_NO,
            ACCOUNT_REF,
            POLICY_NO,
            SESSION_ID_VALUE,
            SNAPSHOT_ERROR,
            INVALID_BODY_ERROR);
  }

  private static InvestmentChoiceSnapshotState snapshot(List<String> pendingRefNos) {
    return InvestmentChoiceSnapshotState.normal(
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF, POLICY_NO, 20, List.of(), List.of(), pendingRefNos));
  }

  private static Stream<Arguments> invalidBodies() {
    return Stream.of(
        invalid(
            "missing body",
            null,
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "null root",
            "null",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "array root",
            "[]",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "boolean root",
            "false",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "number root",
            "1",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "unknown member",
            "{\"other\":true}",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "null reference",
            "{\"referenceNo\":null}",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "numeric reference",
            "{\"referenceNo\":1}",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "boolean reference",
            "{\"referenceNo\":true}",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "array reference",
            "{\"referenceNo\":[]}",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "object reference",
            "{\"referenceNo\":{}}",
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID),
        invalid(
            "missing reference",
            "{}",
            PasswordValidationPrecheckViolation.Kind.FIELD,
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
            "referenceNo"),
        invalid(
            "blank reference",
            "{\"referenceNo\":\" \\t \"}",
            PasswordValidationPrecheckViolation.Kind.FIELD,
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
            "referenceNo"));
  }

  private static Arguments invalid(
      String caseName,
      String body,
      PasswordValidationPrecheckViolation.Kind kind,
      String code,
      String... targets) {
    return Arguments.of(caseName, body, List.of(precheckViolation(kind, code, targets)));
  }

  private static PasswordValidationPrecheckViolation precheckViolation(
      PasswordValidationPrecheckViolation.Kind kind, String code, String... targets) {
    return new PasswordValidationPrecheckViolation(kind, code, List.of(targets));
  }
}
