package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApimEnvelopeSchemaClassificationTest {

  @Test
  void malformedFactoryRequiresNonNullReason() {
    assertThatThrownBy(
            () ->
                new ApimEnvelopeSchemaClassification(
                    ApimEnvelopeSchemaClassification.Kind.MALFORMED, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("malformedReason must not be null when kind is MALFORMED");
  }

  @Test
  void malformedFactoryCarriesTheSuppliedReason() {
    var classification = ApimEnvelopeSchemaClassification.malformed("some reason");
    assertThat(classification.kind()).isEqualTo(ApimEnvelopeSchemaClassification.Kind.MALFORMED);
    assertThat(classification.malformedReason()).isEqualTo("some reason");
  }

  @Test
  void businessRejectedAndSuccessFactoriesProduceNullReason() {
    assertThat(ApimEnvelopeSchemaClassification.businessRejected().malformedReason()).isNull();
    assertThat(ApimEnvelopeSchemaClassification.businessRejected().kind())
        .isEqualTo(ApimEnvelopeSchemaClassification.Kind.BUSINESS_REJECTED);
    assertThat(ApimEnvelopeSchemaClassification.success().malformedReason()).isNull();
    assertThat(ApimEnvelopeSchemaClassification.success().kind())
        .isEqualTo(ApimEnvelopeSchemaClassification.Kind.SUCCESS);
  }

  @Test
  void rejectsNullKind() {
    assertThatThrownBy(() -> new ApimEnvelopeSchemaClassification(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("kind must not be null");
  }
}
