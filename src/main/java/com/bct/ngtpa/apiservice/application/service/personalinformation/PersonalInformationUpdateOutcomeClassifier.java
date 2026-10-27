package com.bct.ngtpa.apiservice.application.service.personalinformation;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PersonalInformationUpdateOutcomeClassifier {
  public enum Outcome {
    SS,
    SF,
    FS,
    FF,
    SYSTEM_ERROR
  }

  public enum Failure {
    INVALID_IDENTIFIERS,
    MISSING_SELECTED_ACCOUNT,
    DUPLICATE_SELECTED_ACCOUNT,
    SELECTED_ERRORS_MISSING
  }

  public record Classification(
      Outcome outcome,
      Failure failure,
      List<UpdatePersonalInformationResult> rows,
      int discardedOtherAccounts) {
    public Classification {
      rows = List.copyOf(rows);
    }
  }

  private final AccountIdentifierFormatter formatter;

  public PersonalInformationUpdateOutcomeClassifier(AccountIdentifierFormatter formatter) {
    this.formatter = Objects.requireNonNull(formatter);
  }

  public Classification classify(
      UpdateMemberInfoCommand command, List<UpdatePersonalInformationResult> rows) {
    if (rows == null || rows.isEmpty()) return systemError(Failure.MISSING_SELECTED_ACCOUNT);
    var matched = new ArrayList<UpdatePersonalInformationResult>();
    for (var row : rows) {
      if (row == null
          || !validNumber(row.policyNo(), formatter.policyWidth())
          || !validNumber(row.certNo(), formatter.certificateWidth())
          || row.env() == null
          || row.env().isBlank()) {
        return systemError(Failure.INVALID_IDENTIFIERS);
      }
      boolean selected =
          normalize(row.policyNo()).equals(normalize(command.policyNo()))
              && normalize(row.certNo()).equals(normalize(command.certNo()))
              && row.env()
                  .trim()
                  .equals(command.accountEnv() == null ? "" : command.accountEnv().trim());
      matched.add(
          new UpdatePersonalInformationResult(
              selected,
              row.success(),
              row.policyNo(),
              row.certNo(),
              row.env(),
              row.refNo(),
              row.submitDate(),
              row.submitTime(),
              row.errors()));
    }
    var selectedRows = matched.stream().filter(UpdatePersonalInformationResult::selected).toList();
    if (selectedRows.isEmpty()) return systemError(Failure.MISSING_SELECTED_ACCOUNT);
    if (selectedRows.size() > 1) return systemError(Failure.DUPLICATE_SELECTED_ACCOUNT);
    var selected = selectedRows.getFirst();
    if (!selected.success() && selected.errors().isEmpty()) {
      return systemError(Failure.SELECTED_ERRORS_MISSING);
    }
    int discarded = 0;
    if (!Boolean.TRUE.equals(command.applyToAllAccounts())) {
      discarded = matched.size() - 1;
      matched.removeIf(row -> !row.selected());
    }
    boolean otherFailed = matched.stream().anyMatch(row -> !row.selected() && !row.success());
    Outcome outcome =
        selected.success()
            ? (otherFailed ? Outcome.SF : Outcome.SS)
            : (otherFailed ? Outcome.FF : Outcome.FS);
    return new Classification(outcome, null, matched, discarded);
  }

  private static boolean validNumber(String value, int width) {
    if (value == null) return false;
    String trimmed = value.trim();
    return trimmed.length() <= width
        && trimmed.matches("[0-9]+")
        && trimmed.chars().anyMatch(character -> character != '0');
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().replaceFirst("^0+(?!$)", "");
  }

  private static Classification systemError(Failure failure) {
    return new Classification(Outcome.SYSTEM_ERROR, failure, List.of(), 0);
  }
}
