package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateIdentifierCryptoTest {
  @Test
  void requestAndResponseIdentifiersSurviveRealFieldCryptoAsPaddedStrings() throws Exception {
    var json = new ObjectMapper();
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var keys = generator.generateKeyPair();
    var properties = new ApimProperties();
    properties.getEncryption().setEnabled(true);
    properties.getEncryption().setRequestFields(List.of("policy-no", "cert-no"));
    properties.getEncryption().setResponseFields(List.of("policy-no", "cert-no"));
    var certificates = mock(ApimAppCertificateService.class);
    when(certificates.getAppPrivateKey()).thenReturn(keys.getPrivate());
    var crypto =
        new ApimPayloadCryptoService(
            properties,
            json,
            new ApimPayloadFieldTransformer(),
            new ApimRsaPayloadCrypto(json),
            new ApimAesKeyFactory(),
            certificates);
    var request =
        UpdateMemberInfoApimRequest.builder()
            .policyNo("00000000118")
            .certNo("0003")
            .applyAll(true)
            .build();
    String path = "/ws/NGTPA/v1/TRPUpdMemberInfo";

    var encryptedRequest =
        crypto.encryptRequest(path, request, UpdateMemberInfoApimRequest.class, keys.getPublic());

    assertThat(encryptedRequest.getPolicyNo()).isNotEqualTo(request.getPolicyNo()).contains(":");
    assertThat(encryptedRequest.getCertNo()).isNotEqualTo(request.getCertNo()).contains(":");
    assertThat(json.valueToTree(encryptedRequest).path("policy-no").isTextual()).isTrue();
    assertThat(json.valueToTree(encryptedRequest).path("cert-no").isTextual()).isTrue();
    assertThat(json.valueToTree(encryptedRequest).path("apply-all").booleanValue()).isTrue();

    var decryptedRequest =
        crypto.decryptResponse(
            path,
            json.writeValueAsString(encryptedRequest),
            UpdateMemberInfoApimRequest.class,
            keys.getPublic());
    assertThat(decryptedRequest.getPolicyNo()).isEqualTo("00000000118");
    assertThat(decryptedRequest.getCertNo()).isEqualTo("0003");

    var encryptedResponse =
        crypto.encryptRequest(
            path,
            Map.of(
                "response",
                Map.of(
                    "err-message",
                    "",
                    "data",
                    List.of(
                        Map.of(
                            "success", true,
                            "policy-no", "00000000118",
                            "cert-no", "0003",
                            "env", "DB")))),
            Map.class,
            keys.getPublic());
    var wireRow = json.valueToTree(encryptedResponse).path("response").path("data").path(0);
    assertThat(wireRow.path("policy-no").isTextual()).isTrue();
    assertThat(wireRow.path("policy-no").asText()).isNotEqualTo("00000000118");
    assertThat(wireRow.path("cert-no").isTextual()).isTrue();
    assertThat(wireRow.path("cert-no").asText()).isNotEqualTo("0003");

    var decryptedResponse =
        crypto.decryptResponseEnvelope(
            path,
            json.writeValueAsString(encryptedResponse),
            UpdateMemberInfoApimDataItem.class,
            keys.getPublic());
    var row = decryptedResponse.getResponse().getData().getFirst();
    assertThat((Object) row.getPolicyNo()).isInstanceOf(String.class).isEqualTo("00000000118");
    assertThat((Object) row.getCertNo()).isInstanceOf(String.class).isEqualTo("0003");
    assertThat(json.valueToTree(row).path("cert-no").isTextual()).isTrue();
  }
}
