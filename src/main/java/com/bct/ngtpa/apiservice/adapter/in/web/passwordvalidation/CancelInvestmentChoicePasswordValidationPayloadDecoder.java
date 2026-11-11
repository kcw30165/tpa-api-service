package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class CancelInvestmentChoicePasswordValidationPayloadDecoder
    implements PasswordValidationPurposePayloadDecoder<
        CancelInvestmentChoicePasswordValidationPayload> {

  private static final String REFERENCE_NO = "referenceNo";

  @Override
  public PasswordValidationPurpose purpose() {
    return PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE;
  }

  @Override
  public Class<CancelInvestmentChoicePasswordValidationPayload> payloadType() {
    return CancelInvestmentChoicePasswordValidationPayload.class;
  }

  @Override
  public CancelInvestmentChoicePasswordValidationPayload decode(JsonNode operationPayload) {
    JsonNode referenceNo = member(operationPayload);
    JsonKind referenceNoKind = kind(referenceNo);
    return new CancelInvestmentChoicePasswordValidationPayload(
        kind(operationPayload),
        memberNames(operationPayload),
        referenceNoKind,
        value(referenceNo, referenceNoKind));
  }

  private static JsonNode member(JsonNode node) {
    return node == null ? null : node.get(REFERENCE_NO);
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

  private static String value(JsonNode node, JsonKind kind) {
    if (kind == JsonKind.MISSING || kind == JsonKind.NULL) {
      return null;
    }
    return node.isValueNode() ? node.asText() : node.toString();
  }

  @Override
  public String toString() {
    return "CancelInvestmentChoicePasswordValidationPayloadDecoder";
  }
}
