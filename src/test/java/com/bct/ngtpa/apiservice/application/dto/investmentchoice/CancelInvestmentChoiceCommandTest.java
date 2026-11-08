package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CancelInvestmentChoiceCommandTest {

  private static final String ACTOR_USER_ID = "actor-user-sensitive";
  private static final String ACCOUNT_ENV = "SIT";
  private static final String POLICY_NO = "policy-sensitive";
  private static final String CERT_NO = "cert-sensitive";
  private static final String SNAPSHOT_ACCOUNT_REF = "snapshot-account-sensitive";
  private static final String REQUEST_ID = "request-safe";
  private static final String OTHER_ACTOR_USER_ID = "actor";
  private static final String OTHER_POLICY_NO = "policy";
  private static final String OTHER_CERT_NO = "cert";
  private static final String OTHER_SNAPSHOT_ACCOUNT_REF = "snapshot-account";
  private static final String REFERENCE_NO = "260001449";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_session-sensitive-0123456789");

  @ParameterizedTest
  @MethodSource("requiredFields")
  void rejectsNullRequiredFields(String field, int fieldIndex) {
    assertThatThrownBy(() -> commandWithNull(fieldIndex))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(field + " must not be null");
  }

  static Stream<Arguments> requiredFields() {
    return Stream.of(
        Arguments.of("actorUserId", 0),
        Arguments.of("actorUserRole", 1),
        Arguments.of("accountEnv", 2),
        Arguments.of("policyNo", 3),
        Arguments.of("certNo", 4),
        Arguments.of("snapshotAccountRef", 5),
        Arguments.of("sessionId", 6),
        Arguments.of("referenceNo", 7));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rejectsBlankReferenceNumber(String referenceNo) {
    assertThatThrownBy(() -> command(referenceNo, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("referenceNo must not be blank");
  }

  @Test
  void allowsNullRequestIdAndRedactsSensitiveCommandValues() {
    var command = command(REFERENCE_NO, null);

    assertThat(command.requestId()).isNull();
    assertThat(command.toString())
        .doesNotContain(
            ACTOR_USER_ID,
            POLICY_NO,
            CERT_NO,
            SNAPSHOT_ACCOUNT_REF,
            SESSION_ID.value(),
            REFERENCE_NO);
  }

  private static CancelInvestmentChoiceCommand command(String referenceNo, String requestId) {
    return new CancelInvestmentChoiceCommand(
        ACTOR_USER_ID,
        ActorUserRole.ROLE_USER_MBR,
        ACCOUNT_ENV,
        POLICY_NO,
        CERT_NO,
        SNAPSHOT_ACCOUNT_REF,
        SESSION_ID,
        requestId,
        referenceNo);
  }

  private static CancelInvestmentChoiceCommand commandWithNull(int fieldIndex) {
    return switch (fieldIndex) {
      case 0 ->
          new CancelInvestmentChoiceCommand(
              null,
              ActorUserRole.ROLE_USER_MBR,
              ACCOUNT_ENV,
              OTHER_POLICY_NO,
              OTHER_CERT_NO,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              SESSION_ID,
              REQUEST_ID,
              REFERENCE_NO);
      case 1 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              null,
              ACCOUNT_ENV,
              OTHER_POLICY_NO,
              OTHER_CERT_NO,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              SESSION_ID,
              REQUEST_ID,
              REFERENCE_NO);
      case 2 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              ActorUserRole.ROLE_USER_MBR,
              null,
              OTHER_POLICY_NO,
              OTHER_CERT_NO,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              SESSION_ID,
              REQUEST_ID,
              REFERENCE_NO);
      case 3 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              ActorUserRole.ROLE_USER_MBR,
              ACCOUNT_ENV,
              null,
              OTHER_CERT_NO,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              SESSION_ID,
              REQUEST_ID,
              REFERENCE_NO);
      case 4 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              ActorUserRole.ROLE_USER_MBR,
              ACCOUNT_ENV,
              OTHER_POLICY_NO,
              null,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              SESSION_ID,
              REQUEST_ID,
              REFERENCE_NO);
      case 5 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              ActorUserRole.ROLE_USER_MBR,
              ACCOUNT_ENV,
              OTHER_POLICY_NO,
              OTHER_CERT_NO,
              null,
              SESSION_ID,
              REQUEST_ID,
              REFERENCE_NO);
      case 6 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              ActorUserRole.ROLE_USER_MBR,
              ACCOUNT_ENV,
              OTHER_POLICY_NO,
              OTHER_CERT_NO,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              null,
              REQUEST_ID,
              REFERENCE_NO);
      case 7 ->
          new CancelInvestmentChoiceCommand(
              OTHER_ACTOR_USER_ID,
              ActorUserRole.ROLE_USER_MBR,
              ACCOUNT_ENV,
              OTHER_POLICY_NO,
              OTHER_CERT_NO,
              OTHER_SNAPSHOT_ACCOUNT_REF,
              SESSION_ID,
              REQUEST_ID,
              null);
      default -> throw new IllegalArgumentException("unknown field index");
    };
  }
}
