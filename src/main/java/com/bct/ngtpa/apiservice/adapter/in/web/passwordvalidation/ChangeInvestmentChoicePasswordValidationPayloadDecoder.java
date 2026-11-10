package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.Allocation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonScalar;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class ChangeInvestmentChoicePasswordValidationPayloadDecoder
    implements PasswordValidationPurposePayloadDecoder<
        ChangeInvestmentChoicePasswordValidationPayload> {

  private static final String SWITCH_SOURCES = "switchSources";
  private static final String ALLOCATIONS = "allocations";
  private static final String FUND_CODE = "fundCode";
  private static final String PERCENTAGE = "percentage";
  private static final JsonScalar MISSING_SCALAR =
      new JsonScalar(JsonKind.MISSING, null, false, null);

  @Override
  public PasswordValidationPurpose purpose() {
    return PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE;
  }

  @Override
  public Class<ChangeInvestmentChoicePasswordValidationPayload> payloadType() {
    return ChangeInvestmentChoicePasswordValidationPayload.class;
  }

  @Override
  public ChangeInvestmentChoicePasswordValidationPayload decode(JsonNode operationPayload) {
    JsonNode switchSources = member(operationPayload, SWITCH_SOURCES);
    JsonNode allocations = member(operationPayload, ALLOCATIONS);
    return new ChangeInvestmentChoicePasswordValidationPayload(
        kind(operationPayload),
        memberNames(operationPayload),
        kind(switchSources),
        scalarArray(switchSources),
        kind(allocations),
        allocationArray(allocations));
  }

  private static JsonNode member(JsonNode node, String name) {
    return node == null ? null : node.get(name);
  }

  private static JsonKind kind(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return JsonKind.MISSING;
    }
    if (node.isNull()) {
      return JsonKind.NULL;
    }
    if (node.isObject()) {
      return JsonKind.OBJECT;
    }
    if (node.isArray()) {
      return JsonKind.ARRAY;
    }
    if (node.isTextual()) {
      return JsonKind.STRING;
    }
    if (node.isBoolean()) {
      return JsonKind.BOOLEAN;
    }
    if (node.isNumber()) {
      return JsonKind.NUMBER;
    }
    return JsonKind.OTHER;
  }

  private static List<String> memberNames(JsonNode node) {
    if (node == null || !node.isObject()) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return List.copyOf(names);
  }

  private static List<JsonScalar> scalarArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<JsonScalar> values = new ArrayList<>();
    for (JsonNode value : node) {
      values.add(scalar(value));
    }
    return List.copyOf(values);
  }

  private static List<Allocation> allocationArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<Allocation> values = new ArrayList<>();
    for (JsonNode allocation : node) {
      values.add(
          new Allocation(
              kind(allocation),
              memberNames(allocation),
              scalar(member(allocation, FUND_CODE)),
              scalar(member(allocation, PERCENTAGE))));
    }
    return List.copyOf(values);
  }

  private static JsonScalar scalar(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return MISSING_SCALAR;
    }
    boolean integralNumber = node.isIntegralNumber();
    Integer integerValue = integralNumber && node.canConvertToInt() ? node.intValue() : null;
    String scalarValue = node.isValueNode() && !node.isNull() ? node.asText() : null;
    return new JsonScalar(kind(node), scalarValue, integralNumber, integerValue);
  }

  @Override
  public String toString() {
    return "ChangeInvestmentChoicePasswordValidationPayloadDecoder";
  }
}
