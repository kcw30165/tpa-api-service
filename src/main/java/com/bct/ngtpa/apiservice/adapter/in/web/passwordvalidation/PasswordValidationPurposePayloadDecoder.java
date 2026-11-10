package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.databind.JsonNode;

public interface PasswordValidationPurposePayloadDecoder<
    T extends PasswordValidationPurposePayload> {

  PasswordValidationPurpose purpose();

  Class<T> payloadType();

  T decode(JsonNode operationPayload);
}
