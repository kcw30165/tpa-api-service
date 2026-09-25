package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimEnvelopeSchemaClassifierTest {

  @Test
  void nullEnvelopeIsMalformedMissingResponse() {
    var classification = ApimEnvelopeSchemaClassifier.classify(null);
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.MALFORMED);
    assertThat(classification.malformedReason()).isEqualTo("response is missing");
  }

  @Test
  void missingResponseObjectIsMalformed() {
    ApimResponseEnvelope<String> envelope = new ApimResponseEnvelope<>();
    var classification = ApimEnvelopeSchemaClassifier.classify(envelope);
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.MALFORMED);
    assertThat(classification.malformedReason()).isEqualTo("response is missing");
  }

  @Test
  void missingErrMessageIsMalformed() {
    var classification = ApimEnvelopeSchemaClassifier.classify(envelope(null, List.of("x")));
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.MALFORMED);
    assertThat(classification.malformedReason()).isEqualTo("response.err-message is missing");
  }

  @Test
  void missingDataWithEmptyErrMessageIsMalformed() {
    var classification = ApimEnvelopeSchemaClassifier.classify(envelope("", null));
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.MALFORMED);
    assertThat(classification.malformedReason())
        .isEqualTo("response.data must be non-empty when response.err-message is empty");
  }

  @Test
  void emptyErrMessageWithEmptyDataArrayIsMalformed() {
    // Regression test for a confirmed production incident: APIM returned
    // {"response":{"err-message":"","data":[]}} for TRPMemberLogin, which the caller could not
    // interpret as a usable success and which ultimately surfaced as an unhandled SYSTEM_ERROR/502
    // with no APIM-layer log at all. An empty data array combined with an empty err-message is a
    // downstream contract violation, not a valid "no records" success. See architecture-and-design.md
    // section 12.
    var classification = ApimEnvelopeSchemaClassifier.classify(envelope("", List.of()));
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.MALFORMED);
    assertThat(classification.malformedReason())
        .isEqualTo("response.data must be non-empty when response.err-message is empty");
  }

  @Test
  void emptyErrMessageWithPopulatedDataIsSuccess() {
    var classification =
        ApimEnvelopeSchemaClassifier.classify(envelope("", List.of("record-1", "record-2")));
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.SUCCESS);
    assertThat(classification.malformedReason()).isNull();
  }

  @Test
  void nonEmptyErrMessageWithMissingDataIsBusinessRejectedNotMalformed() {
    var classification =
        ApimEnvelopeSchemaClassifier.classify(envelope("err.tpa.member.not.found", null));
    assertThat(classification.kind())
        .isEqualTo(ApimEnvelopeSchemaClassification.Kind.BUSINESS_REJECTED);
    assertThat(classification.malformedReason()).isNull();
  }

  @Test
  void nonEmptyErrMessageWithEmptyDataArrayIsBusinessRejectedNotMalformed() {
    var classification =
        ApimEnvelopeSchemaClassifier.classify(envelope("err.tpa.wrong.password", List.of()));
    assertThat(classification.kind())
        .isEqualTo(ApimEnvelopeSchemaClassification.Kind.BUSINESS_REJECTED);
  }

  @Test
  void nonEmptyErrMessageWithPresentDataIsBusinessRejected() {
    var classification =
        ApimEnvelopeSchemaClassifier.classify(envelope("err.tpa.wrong.password", List.of("x")));
    assertThat(classification.kind())
        .isEqualTo(ApimEnvelopeSchemaClassification.Kind.BUSINESS_REJECTED);
  }

  private ApimResponseEnvelope<String> envelope(String errMessage, List<String> data) {
    return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
  }
}
