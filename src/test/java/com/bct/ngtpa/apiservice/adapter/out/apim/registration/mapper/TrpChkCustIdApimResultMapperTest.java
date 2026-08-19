package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpChkCustIdApimResultMapperTest {
  private final TrpChkCustIdApimResultMapper mapper = new TrpChkCustIdApimResultMapper();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesExpandedForgotPasswordPayloadAdditively() throws Exception {
    var item =
        objectMapper.readValue(
            "{\"cust-id\":\"db01\",\"isExist\":true,\"scheme-code\":\"DBA\",\"user-id\":\"3010008\",\"id-type\":\"STAFF\"}",
            TrpChkCustIdApimDataItem.class);

    assertThat(item.customerId()).isEqualTo("db01");
    assertThat(item.isExist()).isTrue();
    assertThat(item.schemeCode()).isEqualTo("DBA");
    assertThat(item.userId()).isEqualTo("3010008");
    assertThat(item.idType()).isEqualTo("STAFF");
  }

  @Test
  void legacyJsonStillDeserializesWhenExpandedFieldsAreAbsent() throws Exception {
    var item =
        objectMapper.readValue(
            "{\"cust-id\":\"db01\",\"isExist\":false}", TrpChkCustIdApimDataItem.class);

    assertThat(item.customerId()).isEqualTo("db01");
    assertThat(item.isExist()).isFalse();
    assertThat(item.schemeCode()).isNull();
    assertThat(item.userId()).isNull();
    assertThat(item.idType()).isNull();
  }

  @Test
  void mapsIsExistFalseToAvailable() throws Exception {
    var item =
        objectMapper.readValue(
            "{\"cust-id\":\"db01\",\"isExist\":false}", TrpChkCustIdApimDataItem.class);

    var result = mapper.toResult(new TrpChkCustIdApimEnvelope("", java.util.List.of(item)));

    assertThat(item.customerId()).isEqualTo("db01");
    assertThat(item.isExist()).isFalse();
    assertThat(result.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.AVAILABLE);
    assertThat(result.downstreamErrorCode()).isNull();
  }

  @Test
  void mapsIsExistTrueToRejectedWithRawDownstreamCodeForClassifier() throws Exception {
    var item =
        objectMapper.readValue(
            "{\"cust-id\":\"db01\",\"isExist\":true}", TrpChkCustIdApimDataItem.class);

    var result = mapper.toResult(new TrpChkCustIdApimEnvelope("", java.util.List.of(item)));

    assertThat(item.customerId()).isEqualTo("db01");
    assertThat(item.isExist()).isTrue();
    assertThat(result.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.REJECTED);
    assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.username.taken");
    assertThat(result.downstreamErrorCode()).isNotEqualTo("registration.username.isregistered");
  }

  @Test
  void explicitErrMessageStillWinsOverData() {
    var result =
        mapper.toResult(
            new TrpChkCustIdApimEnvelope(
                "err.tpa.some.business.error",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", Boolean.FALSE))));

    assertThat(result.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.REJECTED);
    assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.some.business.error");
  }

  @Test
  void toStringDoesNotExposeCustomerId() {
    var item = new TrpChkCustIdApimDataItem("db01", Boolean.TRUE);
    var envelope = new TrpChkCustIdApimEnvelope("", java.util.List.of(item));

    assertThat(item.toString()).doesNotContain("db01");
    assertThat(envelope.toString()).doesNotContain("db01");
  }
}
