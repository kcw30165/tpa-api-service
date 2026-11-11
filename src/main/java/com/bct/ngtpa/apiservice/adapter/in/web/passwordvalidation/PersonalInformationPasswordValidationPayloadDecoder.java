package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PersonalInformationPasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PersonalInformationPasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class PersonalInformationPasswordValidationPayloadDecoder
    implements PasswordValidationPurposePayloadDecoder<
        PersonalInformationPasswordValidationPayload> {

  private static final String FORM_VERSION = "formVersion";
  private static final String APPLY_TO_ALL_ACCOUNTS = "applyToAllAccounts";
  private static final String FIELDS = "fields";

  @Override
  public PasswordValidationPurpose purpose() {
    return PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE;
  }

  @Override
  public Class<PersonalInformationPasswordValidationPayload> payloadType() {
    return PersonalInformationPasswordValidationPayload.class;
  }

  @Override
  public PersonalInformationPasswordValidationPayload decode(JsonNode operationPayload) {
    JsonNode formVersionNode = member(operationPayload, FORM_VERSION);
    JsonNode applyToAllAccountsNode = member(operationPayload, APPLY_TO_ALL_ACCOUNTS);
    JsonNode fieldsNode = member(operationPayload, FIELDS);

    return new PersonalInformationPasswordValidationPayload(
        kind(operationPayload),
        hasMember(operationPayload, FORM_VERSION),
        kind(formVersionNode),
        textualValue(formVersionNode),
        hasMember(operationPayload, APPLY_TO_ALL_ACCOUNTS),
        kind(applyToAllAccountsNode),
        booleanValue(applyToAllAccountsNode),
        hasMember(operationPayload, FIELDS),
        kind(fieldsNode),
        memberNames(operationPayload),
        memberNames(fieldsNode));
  }

  private static JsonNode member(JsonNode node, String name) {
    return node == null ? null : node.get(name);
  }

  private static boolean hasMember(JsonNode node, String name) {
    return node != null && node.has(name);
  }

  private static String textualValue(JsonNode node) {
    return node != null && node.isTextual() ? node.textValue() : null;
  }

  private static Boolean booleanValue(JsonNode node) {
    return node != null && node.isBoolean() ? node.booleanValue() : null;
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
}
