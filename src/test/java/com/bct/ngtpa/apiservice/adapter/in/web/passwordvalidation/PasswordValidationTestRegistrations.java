package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PersonalInformationPasswordValidationPayload;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PasswordValidationTestRegistrations {
  private PasswordValidationTestRegistrations() {}

  static PasswordValidationPurposePayloadDecoderRegistry decoders() {
    return new PasswordValidationPurposePayloadDecoderRegistry(
        List.of(
            new PersonalInformationPasswordValidationPayloadDecoder(),
            new ChangeInvestmentChoicePasswordValidationPayloadDecoder(),
            new CancelInvestmentChoicePasswordValidationPayloadDecoder()),
        Set.of(PasswordValidationPurpose.values()),
        Map.of(
            PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
            PersonalInformationPasswordValidationPayload.class,
            PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE,
            ChangeInvestmentChoicePasswordValidationPayload.class,
            PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE,
            CancelInvestmentChoicePasswordValidationPayload.class));
  }
}
