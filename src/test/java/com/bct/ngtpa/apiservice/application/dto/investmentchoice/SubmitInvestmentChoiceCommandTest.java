package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SubmitInvestmentChoiceCommandTest {

  private static final InvestmentChoiceInstructionRequest REQUEST =
      new InvestmentChoiceInstructionRequest(
          List.of("BO"), List.of(new InvestmentChoiceInstructionRequest.Allocation("FBAL", 100)));

  @ParameterizedTest
  @MethodSource("requiredFields")
  void rejectsNullRequiredFields(String field, int fieldIndex) {
    assertThatThrownBy(() -> commandWithNull(fieldIndex))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(field + " must not be null");
  }

  private static Stream<Arguments> requiredFields() {
    return Stream.of(
        Arguments.of("actorUserId", 0),
        Arguments.of("actorUserRole", 1),
        Arguments.of("accountEnv", 2),
        Arguments.of("policyNo", 3),
        Arguments.of("certNo", 4),
        Arguments.of("snapshotAccountRef", 5),
        Arguments.of("sessionId", 6),
        Arguments.of("request", 7),
        Arguments.of("authorizationBinding", 8));
  }

  @Test
  void permitsNullRequestIdAndRedactsSensitiveCommandValues() {
    var command =
        new SubmitInvestmentChoiceCommand(
            "actor-sensitive",
            ActorUserRole.ROLE_USER_MBR,
            "SIT",
            "policy-sensitive",
            "cert-sensitive",
            "snapshot-account-sensitive",
            new AuthenticatedSessionId("sid_session-sensitive-0123456789"),
            null,
            REQUEST,
            binding());

    assertThat(command.requestId()).isNull();
    assertThat(command.toString())
        .doesNotContain(
            "actor-sensitive",
            "policy-sensitive",
            "cert-sensitive",
            "snapshot-account-sensitive",
            "sid_session-sensitive-0123456789",
            "FBAL",
            "100");
  }

  private static SubmitInvestmentChoiceCommand commandWithNull(int fieldIndex) {
    var sessionId = new AuthenticatedSessionId("sid_session-sensitive-0123456789");
    return switch (fieldIndex) {
      case 0 ->
          new SubmitInvestmentChoiceCommand(
              null,
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              "policy",
              "cert",
              "account-ref",
              sessionId,
              "request",
              REQUEST,
              binding());
      case 1 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              null,
              "SIT",
              "policy",
              "cert",
              "account-ref",
              sessionId,
              "request",
              REQUEST,
              binding());
      case 2 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              null,
              "policy",
              "cert",
              "account-ref",
              sessionId,
              "request",
              REQUEST,
              binding());
      case 3 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              null,
              "cert",
              "account-ref",
              sessionId,
              "request",
              REQUEST,
              binding());
      case 4 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              "policy",
              null,
              "account-ref",
              sessionId,
              "request",
              REQUEST,
              binding());
      case 5 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              "policy",
              "cert",
              null,
              sessionId,
              "request",
              REQUEST,
              binding());
      case 6 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              "policy",
              "cert",
              "account-ref",
              null,
              "request",
              REQUEST,
              binding());
      case 7 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              "policy",
              "cert",
              "account-ref",
              sessionId,
              "request",
              null,
              binding());
      case 8 ->
          new SubmitInvestmentChoiceCommand(
              "actor",
              ActorUserRole.ROLE_USER_MBR,
              "SIT",
              "policy",
              "cert",
              "account-ref",
              sessionId,
              "request",
              REQUEST,
              null);
      default -> throw new IllegalArgumentException("unknown field index");
    };
  }

  private static PayloadDigest binding() {
    return new PayloadDigest(1, "a".repeat(64));
  }
}
