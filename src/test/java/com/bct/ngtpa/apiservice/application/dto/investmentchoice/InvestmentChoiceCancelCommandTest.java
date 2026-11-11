package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvestmentChoiceCancelCommandTest {
  private static final String ACTOR_ID = "USER1234";
  private static final String ACCOUNT_ENV = "DB";
  private static final String POLICY_NUMBER = "POLICY1234";
  private static final String CERTIFICATE_NUMBER = "CERT1234";
  private static final String REFERENCE_NUMBER = "260001453";

  @ParameterizedTest
  @MethodSource("nullCommandFields")
  void rejectsNullFields(int field) {
    assertThatThrownBy(() -> commandWithNullField(field)).isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void rejectsBlankReferenceNumber(String referenceNo) {
    assertThatThrownBy(() -> command(referenceNo)).isInstanceOf(IllegalArgumentException.class);
  }

  static Stream<Arguments> nullCommandFields() {
    return Stream.of(
        Arguments.of(0),
        Arguments.of(1),
        Arguments.of(2),
        Arguments.of(3),
        Arguments.of(4),
        Arguments.of(5));
  }

  private static InvestmentChoiceCancelCommand commandWithNullField(int field) {
    return switch (field) {
      case 0 ->
          new InvestmentChoiceCancelCommand(
              null,
              ActorUserRole.ROLE_INPUT,
              ACCOUNT_ENV,
              POLICY_NUMBER,
              CERTIFICATE_NUMBER,
              REFERENCE_NUMBER);
      case 1 ->
          new InvestmentChoiceCancelCommand(
              ACTOR_ID, null, ACCOUNT_ENV, POLICY_NUMBER, CERTIFICATE_NUMBER, REFERENCE_NUMBER);
      case 2 ->
          new InvestmentChoiceCancelCommand(
              ACTOR_ID,
              ActorUserRole.ROLE_INPUT,
              null,
              POLICY_NUMBER,
              CERTIFICATE_NUMBER,
              REFERENCE_NUMBER);
      case 3 ->
          new InvestmentChoiceCancelCommand(
              ACTOR_ID,
              ActorUserRole.ROLE_INPUT,
              ACCOUNT_ENV,
              null,
              CERTIFICATE_NUMBER,
              REFERENCE_NUMBER);
      case 4 ->
          new InvestmentChoiceCancelCommand(
              ACTOR_ID,
              ActorUserRole.ROLE_INPUT,
              ACCOUNT_ENV,
              POLICY_NUMBER,
              null,
              REFERENCE_NUMBER);
      case 5 ->
          new InvestmentChoiceCancelCommand(
              ACTOR_ID,
              ActorUserRole.ROLE_INPUT,
              ACCOUNT_ENV,
              POLICY_NUMBER,
              CERTIFICATE_NUMBER,
              null);
      default -> throw new AssertionError(field);
    };
  }

  private static InvestmentChoiceCancelCommand command(String referenceNo) {
    return new InvestmentChoiceCancelCommand(
        ACTOR_ID,
        ActorUserRole.ROLE_INPUT,
        ACCOUNT_ENV,
        POLICY_NUMBER,
        CERTIFICATE_NUMBER,
        referenceNo);
  }
}
