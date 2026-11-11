package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.InvestMandate;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.SwitchFund;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.SwitchSrc;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

final class TrpGetCIMDtlApimScenarioMapper {

  private static final ObjectMapper JSON = new ObjectMapper();

  // Synthetic exact-string mappings are temporary; the Progress wire contract is unconfirmed.
  InvestmentChoiceScenario scenario(JsonNode item) {
    JsonNode value = item.get("scenario");
    if (value == null) {
      return InvestmentChoiceScenario.NORMAL;
    }
    if (!value.isTextual()) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    return switch (value.textValue()) {
      case "NORMAL" -> InvestmentChoiceScenario.NORMAL;
      case "FUNCTION_UNAVAILABLE" -> InvestmentChoiceScenario.FUNCTION_UNAVAILABLE;
      case "MEMBER_INELIGIBLE" -> InvestmentChoiceScenario.MEMBER_INELIGIBLE;
      default -> throw new InvestmentChoiceMalformedResponseException();
    };
  }

  TrpGetCIMDtlApimDataItem normalItem(JsonNode item) {
    try {
      return JSON.treeToValue(item, TrpGetCIMDtlApimDataItem.class);
    } catch (RuntimeException | java.io.IOException exception) {
      throw new InvestmentChoiceMalformedResponseException();
    }
  }

  RestrictedRows restrictedRows(JsonNode item) {
    return new RestrictedRows(
        readRows(item, "SwitchSrc", SwitchSrc.class, false),
        readRows(item, "SwitchFund", SwitchFund.class, true),
        readRows(item, "InvestMandate", InvestMandate.class, true));
  }

  private <T> List<T> readRows(
      JsonNode item, String field, Class<T> elementType, boolean required) {
    JsonNode rows = item.get(field);
    if (rows == null || rows.isNull()) {
      if (required) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      return List.of();
    }
    if (!rows.isArray()) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    try {
      JavaType listType = JSON.getTypeFactory().constructCollectionType(List.class, elementType);
      return List.copyOf(JSON.convertValue(rows, listType));
    } catch (RuntimeException exception) {
      throw new InvestmentChoiceMalformedResponseException();
    }
  }

  record RestrictedRows(
      List<SwitchSrc> sources, List<SwitchFund> funds, List<InvestMandate> mandates) {}
}
