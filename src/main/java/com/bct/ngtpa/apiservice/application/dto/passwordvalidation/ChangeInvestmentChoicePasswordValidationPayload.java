package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record ChangeInvestmentChoicePasswordValidationPayload(
    JsonKind rootKind,
    List<String> rootMembers,
    JsonKind switchSourcesKind,
    List<JsonScalar> switchSources,
    JsonKind allocationsKind,
    List<Allocation> allocations)
    implements PasswordValidationPurposePayload {

  public ChangeInvestmentChoicePasswordValidationPayload {
    Objects.requireNonNull(rootKind, "rootKind must not be null");
    rootMembers = List.copyOf(Objects.requireNonNull(rootMembers, "rootMembers must not be null"));
    Objects.requireNonNull(switchSourcesKind, "switchSourcesKind must not be null");
    switchSources =
        List.copyOf(Objects.requireNonNull(switchSources, "switchSources must not be null"));
    Objects.requireNonNull(allocationsKind, "allocationsKind must not be null");
    allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations must not be null"));
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

  public record JsonScalar(
      JsonKind kind, String scalarValue, boolean integralNumber, Integer integerValue) {
    public JsonScalar {
      Objects.requireNonNull(kind, "kind must not be null");
    }

    @Override
    public String toString() {
      return "JsonScalar[<redacted>]";
    }
  }

  public record Allocation(
      JsonKind kind, List<String> memberNames, JsonScalar fundCode, JsonScalar percentage) {
    public Allocation {
      Objects.requireNonNull(kind, "kind must not be null");
      memberNames =
          List.copyOf(Objects.requireNonNull(memberNames, "memberNames must not be null"));
      Objects.requireNonNull(fundCode, "fundCode must not be null");
      Objects.requireNonNull(percentage, "percentage must not be null");
    }

    @Override
    public String toString() {
      return "Allocation[<redacted>]";
    }
  }

  @Override
  public String toString() {
    return "ChangeInvestmentChoicePasswordValidationPayload[<redacted>]";
  }
}
