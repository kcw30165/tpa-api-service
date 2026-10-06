package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import java.util.List;

/**
 * Pure, side-effect-free classifier for the APIM response envelope schema.
 *
 * <p>Global contract under classification:
 *
 * <ul>
 *   <li>{@code response} must be present.
 *   <li>{@code response.err-message} must be present.
 *   <li>{@code response.data} must be present AND non-empty when {@code err-message} is empty. An
 *       empty {@code data} array with an empty {@code err-message} is treated as a downstream
 *       contract violation (confirmed by a production incident on TRPMemberLogin where APIM
 *       returned {@code {"response":{"err-message":"","data":[]}}} and the caller had no usable
 *       result to interpret), not a valid "no records" success.
 *   <li>{@code response.data} may be absent or empty when {@code err-message} is non-empty - a
 *       business rejection is not required to carry a data array.
 * </ul>
 *
 * <p>This classifier never throws and is used only to decide what {@link
 * ApimExchangeOutcomeLogger} should log. It must not be used to change adapter, mapper, or
 * business-envelope-interpretation behavior. See {@code
 * docs/capabilities/apim-error-logging/architecture-and-design.md} section 5 (and section 12 for
 * the production-incident-driven correction) for the full rationale and worked examples.
 */
public final class ApimEnvelopeSchemaClassifier {

  private ApimEnvelopeSchemaClassifier() {}

  public static ApimEnvelopeSchemaClassification classify(ApimResponseEnvelope<?> envelope) {
    if (envelope == null || envelope.getResponse() == null) {
      return ApimEnvelopeSchemaClassification.malformed("response is missing");
    }
    ApimResponseBody<?> response = envelope.getResponse();
    String errMessage = response.getErrMessage();
    if (errMessage == null) {
      return ApimEnvelopeSchemaClassification.malformed("response.err-message is missing");
    }
    if (!errMessage.isEmpty()) {
      return ApimEnvelopeSchemaClassification.businessRejected();
    }
    List<?> data = response.getData();
    if (data == null || data.isEmpty()) {
      return ApimEnvelopeSchemaClassification.malformed(
          "response.data must be non-empty when response.err-message is empty");
    }
    return ApimEnvelopeSchemaClassification.success();
  }
}
