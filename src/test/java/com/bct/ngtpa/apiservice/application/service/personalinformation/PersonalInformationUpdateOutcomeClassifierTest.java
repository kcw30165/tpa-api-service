package com.bct.ngtpa.apiservice.application.service.personalinformation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PersonalInformationUpdateOutcomeClassifierTest {
  private final PersonalInformationUpdateOutcomeClassifier classifier =
      new PersonalInformationUpdateOutcomeClassifier(new AccountIdentifierFormatter(11, 4, '0'));

  @ParameterizedTest
  @MethodSource("outcomes")
  void classifiesAllAccountSuccessCombinations(
      boolean selectedSuccess, boolean otherSuccess, String expected) {
    var rows =
        List.of(row("118", "3", "DB", selectedSuccess), row("118", "4", "DB", otherSuccess));

    var result = classifier.classify(command(true), rows);

    assertThat(result.outcome().name()).isEqualTo(expected);
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().getFirst().selected()).isTrue();
    assertThat(result.rows().getLast().selected()).isFalse();
    assertThat(result.failure()).isNull();
  }

  static Stream<Arguments> outcomes() {
    return Stream.of(
        Arguments.of(true, true, "SS"),
        Arguments.of(true, false, "SF"),
        Arguments.of(false, true, "FS"),
        Arguments.of(false, false, "FF"));
  }

  @ParameterizedTest
  @MethodSource("invalidRows")
  void invalidIdentifiersAndMissingOrDuplicateSelectedAccountsAreSystemErrors(
      List<UpdatePersonalInformationResult> rows, String failure) {
    var result = classifier.classify(command(true), rows);

    assertThat(result.outcome().name()).isEqualTo("SYSTEM_ERROR");
    assertThat(result.failure().name()).isEqualTo(failure);
    assertThat(result.rows()).isEmpty();
  }

  static Stream<Arguments> invalidRows() {
    return Stream.of(
        Arguments.of(null, "MISSING_SELECTED_ACCOUNT"),
        Arguments.of(List.of(), "MISSING_SELECTED_ACCOUNT"),
        Arguments.of(Arrays.asList((UpdatePersonalInformationResult) null), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row(null, "3", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row(" ", "3", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("abc", "3", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("0", "3", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("123456789012", "3", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", null, "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "abc", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "0", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "-1", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "10000", "DB", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "3", null, true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "3", " ", true)), "INVALID_IDENTIFIERS"),
        Arguments.of(List.of(row("118", "4", "DB", true)), "MISSING_SELECTED_ACCOUNT"),
        Arguments.of(List.of(row("119", "3", "DB", true)), "MISSING_SELECTED_ACCOUNT"),
        Arguments.of(List.of(row("118", "3", "JP", true)), "MISSING_SELECTED_ACCOUNT"),
        Arguments.of(
            List.of(row("118", "3", "DB", true), row("00000000118", "0003", "DB", false)),
            "DUPLICATE_SELECTED_ACCOUNT"),
        Arguments.of(
            List.of(new UpdatePersonalInformationResult(
                false, false, "118", "3", "DB", null, null, null, List.of())),
            "SELECTED_ERRORS_MISSING"),
        Arguments.of(
            List.of(new UpdatePersonalInformationResult(
                false, false, "118", "3", "DB", null, null, null, null)),
            "SELECTED_ERRORS_MISSING"));
  }

  @ParameterizedTest
  @MethodSource("applyFlags")
  void dropsOnlyOtherAccountsWhenApplyAllIsNotTrue(Boolean applyAll) {
    var result =
        classifier.classify(
            command(applyAll),
            List.of(row("118", "3", "DB", true), row("118", "4", "DB", false)));

    assertThat(result.outcome().name()).isEqualTo("SS");
    assertThat(result.discardedOtherAccounts()).isEqualTo(1);
    assertThat(result.rows()).hasSize(1);
  }

  static Stream<Arguments> applyFlags() {
    return Stream.of(Arguments.of((Boolean) null), Arguments.of(false));
  }

  @Test
  void selectedAccountAloneAndBlankPassThroughValuesArePreserved() {
    var selected =
        new UpdatePersonalInformationResult(
            false, true, " 118 ", "3", " DB ", null, "", " ", List.of());
    var result = classifier.classify(command(true), List.of(selected));

    assertThat(result.outcome().name()).isEqualTo("SS");
    var actual = result.rows().getFirst();
    assertThat(actual.refNo()).isNull();
    assertThat(actual.submitDate()).isEmpty();
    assertThat(actual.submitTime()).isEqualTo(" ");
    assertThat(result.discardedOtherAccounts()).isZero();
  }

  @Test
  void failedOtherAccountWithoutErrorsStillClassifiesAsFailureForLaterFallbackMapping() {
    var other =
        new UpdatePersonalInformationResult(
            false, false, "118", "4", "DB", null, null, null, List.of());
    assertThat(classifier.classify(command(true), List.of(row("118", "3", "DB", true), other))
        .outcome().name()).isEqualTo("SF");
  }

  @Test
  void validationUsesConfiguredWidthsRatherThanHardcodedWidths() {
    var narrow =
        new PersonalInformationUpdateOutcomeClassifier(new AccountIdentifierFormatter(2, 1, '0'));
    assertThat(narrow.classify(command(true), List.of(row("118", "3", "DB", true)))
        .failure().name()).isEqualTo("INVALID_IDENTIFIERS");
  }

  private static UpdateMemberInfoCommand command(Boolean applyAll) {
    return new UpdateMemberInfoCommand(
        "DB", "00000000118", "0003", "user", ActorUserRole.ROLE_USER_MBR, applyAll, Map.of());
  }

  private static UpdatePersonalInformationResult row(
      String policy, String cert, String env, boolean success) {
    return new UpdatePersonalInformationResult(
        false, success, policy, cert, env, null, null, null,
        success ? List.of() : List.of(new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED")));
  }
}
