package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRow;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralMetadataApimDtoContractTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesUnfilteredRequestWithExplicitEmptyFilters() throws Exception {
    var request = GeneralMetadataApimRequest.unfiltered("DB", "DBA", "00000008802");
    assertThat(mapper.writeValueAsString(request))
        .isEqualTo(
            "{\"env\":\"DB\",\"trust-code\":\"DBA\",\"policy-no\":\"00000008802\",\"metadata-name\":\"\",\"metadata-type\":\"\"}");
  }

  @Test
  void serializesTargetedRequestWithWireValues() throws Exception {
    var key = new GeneralMetadataKey(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION);
    var request = GeneralMetadataApimRequest.targeted("DB", "DBA", "00000008802", key);
    assertThat(mapper.writeValueAsString(request))
        .isEqualTo(
            "{\"env\":\"DB\",\"trust-code\":\"DBA\",\"policy-no\":\"00000008802\",\"metadata-name\":\"fund-pri\",\"metadata-type\":\"precision\"}");
  }

  @Test
  void deserializesExactResponseThroughCanonicalEnvelopeAndValidator() throws Exception {
    String json =
        "{\"response\":{\"err-message\":\"\",\"data\":[{\"policy-no\":\"all\",\"metadata-name\":\"fund-pri\",\"metadata-type\":\"precision\",\"metadata-value\":\"6\"}],\"future\":true},\"future-top\":true}";
    List<GeneralMetadataApimRow> data =
        ApimResponseValidator.requireSuccessData(mapper.readValue(json, envelopeType()));
    assertThat(data)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.policyNo()).isEqualTo("all");
              assertThat(row.metadataName()).isEqualTo("fund-pri");
              assertThat(row.metadataType()).isEqualTo("precision");
              assertThat(row.metadataValue()).isEqualTo("6");
            });
  }

  @Test
  void canonicalValidatorAcceptsEmptyDataAndRejectsMissingStructure() throws Exception {
    assertThat(
            ApimResponseValidator.requireSuccessData(
                mapper.readValue(
                    "{\"response\":{\"err-message\":\"\",\"data\":[]}}", envelopeType())))
        .isEmpty();
    for (String invalid :
        new String[] {
          "{}", "{\"response\":{\"data\":[]}}", "{\"response\":{\"err-message\":\"\"}}"
        }) {
      assertThatThrownBy(
              () ->
                  ApimResponseValidator.requireSuccessData(
                      mapper.readValue(invalid, envelopeType())))
          .isInstanceOf(ApimException.class);
    }
  }

  private JavaType envelopeType() {
    return mapper
        .getTypeFactory()
        .constructParametricType(ApimResponseEnvelope.class, GeneralMetadataApimRow.class);
  }

  @Test
  void usesAccountEnvironmentNameInJavaAndEnvOnlyOnTheApimWire() throws Exception {
    var request = GeneralMetadataApimRequest.unfiltered("DB", "DBA", "00000008802");
    assertThat(request.accountEnv()).isEqualTo("DB");
    assertThat(GeneralMetadataApimRequest.class.getDeclaredMethods())
        .extracting(java.lang.reflect.Method::getName)
        .doesNotContain("env");
    assertThat(mapper.readTree(mapper.writeValueAsString(request)).get("env").asText())
        .isEqualTo("DB");
  }

  @Test
  void factoryMethodsExposeAccountEnvironmentParametersOnly() throws Exception {
    for (var method : GeneralMetadataApimRequest.class.getDeclaredMethods()) {
      if (method.getName().equals("unfiltered") || method.getName().equals("targeted")) {
        assertThat(method.getParameters()[0].getName()).isEqualTo("accountEnv");
      }
    }
  }
}
