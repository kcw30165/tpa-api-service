package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import java.util.Objects;

public final class InvestmentChoiceExecutionPayloadMapper {

  public InvestmentChoiceInstructionRequest toInstructionRequest(
      ChangeInvestmentChoicePasswordValidationPayload payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    if (payload.rootKind() != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.OBJECT
        || payload.switchSourcesKind()
            != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.ARRAY
        || payload.allocationsKind()
            != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.ARRAY) {
      throw invalidPayload();
    }

    var switchSources =
        payload.switchSources().stream()
            .map(
                source -> {
                  if (source.kind()
                          != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.STRING
                      || source.scalarValue() == null) {
                    throw invalidPayload();
                  }
                  return source.scalarValue();
                })
            .toList();
    var allocations =
        payload.allocations().stream()
            .map(
                allocation -> {
                  var fund = allocation.fundCode();
                  var percentage = allocation.percentage();
                  if (allocation.kind()
                          != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.OBJECT
                      || fund.kind()
                          != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.STRING
                      || fund.scalarValue() == null
                      || percentage.kind()
                          != ChangeInvestmentChoicePasswordValidationPayload.JsonKind.NUMBER
                      || !percentage.integralNumber()
                      || percentage.integerValue() == null) {
                    throw invalidPayload();
                  }
                  return new InvestmentChoiceInstructionRequest.Allocation(
                      fund.scalarValue(), percentage.integerValue());
                })
            .toList();
    return new InvestmentChoiceInstructionRequest(switchSources, allocations);
  }

  public String toReferenceNo(CancelInvestmentChoicePasswordValidationPayload payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    if (payload.rootKind() != CancelInvestmentChoicePasswordValidationPayload.JsonKind.OBJECT
        || payload.referenceNoKind()
            != CancelInvestmentChoicePasswordValidationPayload.JsonKind.STRING
        || payload.referenceNoValue() == null) {
      throw invalidPayload();
    }
    return payload.referenceNoValue();
  }

  private static IllegalArgumentException invalidPayload() {
    return new IllegalArgumentException("Invalid authorized investment choice payload");
  }
}
