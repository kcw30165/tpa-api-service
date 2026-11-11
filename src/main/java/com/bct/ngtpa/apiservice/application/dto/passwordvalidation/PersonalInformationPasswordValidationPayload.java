package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record PersonalInformationPasswordValidationPayload(
    JsonKind operationPayloadKind,
    boolean formVersionPresent,
    JsonKind formVersionKind,
    String formVersion,
    boolean applyToAllAccountsPresent,
    JsonKind applyToAllAccountsKind,
    Boolean applyToAllAccounts,
    boolean fieldsPresent,
    JsonKind fieldsKind,
    List<String> operationMemberNames,
    List<String> fieldsMemberNames)
    implements PasswordValidationPurposePayload {

  public PersonalInformationPasswordValidationPayload {
    Objects.requireNonNull(operationPayloadKind, "operationPayloadKind must not be null");
    Objects.requireNonNull(formVersionKind, "formVersionKind must not be null");
    Objects.requireNonNull(applyToAllAccountsKind, "applyToAllAccountsKind must not be null");
    Objects.requireNonNull(fieldsKind, "fieldsKind must not be null");
    operationMemberNames =
        List.copyOf(
            Objects.requireNonNull(operationMemberNames, "operationMemberNames must not be null"));
    fieldsMemberNames =
        List.copyOf(
            Objects.requireNonNull(fieldsMemberNames, "fieldsMemberNames must not be null"));
  }

  public int fieldsMemberCount() {
    return fieldsMemberNames.size();
  }

  public enum JsonKind {
    MISSING,
    NULL,
    OBJECT,
    ARRAY,
    STRING,
    BOOLEAN,
    NUMBER,
    OTHER
  }

  @Override
  public String toString() {
    return "PersonalInformationPasswordValidationPayload[<redacted>]";
  }
}
