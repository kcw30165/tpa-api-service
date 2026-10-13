package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A response that cannot be mapped to its Java type is an {@link ApimResponseMappingException}, not
 * a decryption failure, whether or not encryption is enabled. Malformed or empty JSON stays a parse
 * failure, and real field decryption failures keep their existing message.
 */
class ApimResponseMappingFailureTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String API = "/ws/NGTPA/v1/TRPGetMemberAccBal";
  private static final String UNKNOWN_ITEM = "{\"known\":\"a\",\"extra\":\"b\"}";
  private static final String WRONG_TYPE_ITEM = "{\"known\":\"a\",\"count\":\"x\"}";
  private static final String UNKNOWN_ENVELOPE =
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"known\":\"a\",\"extra\":\"b\"}]}}";

  public record StrictItem(
      @JsonProperty("known") String known, @JsonProperty("count") Integer count) {}

  @Test
  void encryptedUnknownPropertyIsAMappingFailureNotADecryptFailure() {
    var service = service(true);

    assertThatThrownBy(() -> service.decryptResponse(API, UNKNOWN_ITEM, StrictItem.class, null))
        .isInstanceOf(ApimResponseMappingException.class)
        .isNotInstanceOf(ApimCryptoException.class)
        .hasMessage("Failed to map APIM response payload.")
        .hasCauseInstanceOf(UnrecognizedPropertyException.class);
  }

  @Test
  void encryptedEnvelopeMappingFailureKeepsTheJacksonFieldPath() {
    var service = service(true);

    Throwable error =
        catchThrowable(
            () -> service.decryptResponseEnvelope(API, UNKNOWN_ENVELOPE, StrictItem.class, null));

    assertThat(error).isInstanceOf(ApimResponseMappingException.class);
    assertThat(error.getCause()).isInstanceOf(UnrecognizedPropertyException.class);
    assertThat(((JsonMappingException) error.getCause()).getPath())
        .extracting(JsonMappingException.Reference::getFieldName)
        .contains("response", "data", "extra");
  }

  @Test
  void plainUnknownPropertyIsAMappingFailure() {
    var service = service(false);

    assertThatThrownBy(() -> service.decryptResponse(API, UNKNOWN_ITEM, StrictItem.class))
        .isInstanceOf(ApimResponseMappingException.class)
        .hasCauseInstanceOf(UnrecognizedPropertyException.class);
    assertThatThrownBy(
            () -> service.decryptResponseEnvelope(API, UNKNOWN_ENVELOPE, StrictItem.class))
        .isInstanceOf(ApimResponseMappingException.class)
        .hasCauseInstanceOf(UnrecognizedPropertyException.class);
  }

  @Test
  void plainWrongValueTypeIsAMappingFailure() {
    assertThatThrownBy(() -> service(false).decryptResponse(API, WRONG_TYPE_ITEM, StrictItem.class))
        .isInstanceOf(ApimResponseMappingException.class)
        .hasCauseInstanceOf(InvalidFormatException.class);
  }

  @Test
  void plainEmptyBodyRemainsAParseFailure() {
    var service = service(false);

    assertThatThrownBy(() -> service.decryptResponse(API, "", StrictItem.class))
        .isInstanceOf(ApimCryptoException.class)
        .hasMessage("Failed to parse APIM response payload.");
    assertThatThrownBy(() -> service.decryptResponseEnvelope(API, "", StrictItem.class))
        .isInstanceOf(ApimCryptoException.class)
        .hasMessage("Failed to parse APIM response payload.");
  }

  @Test
  void encryptedMalformedJsonIsAParseFailureNotADecryptFailure() {
    assertThatThrownBy(() -> service(true).decryptResponse(API, "not-json", StrictItem.class, null))
        .isInstanceOf(ApimCryptoException.class)
        .hasMessage("Failed to parse APIM response payload.");
  }

  @Test
  void classifierTreatsMappingFailuresLikeCryptoFailures() {
    assertThat(
            ApimCryptoFailureClassifier.isCryptoFailure(
                new ApimResponseMappingException(new IllegalStateException("x"))))
        .isTrue();
    assertThat(ApimCryptoFailureClassifier.isCryptoFailure(new ApimCryptoException("x"))).isTrue();
    assertThat(ApimCryptoFailureClassifier.isCryptoFailure(new IllegalStateException("x")))
        .isFalse();
  }

  private static ApimPayloadCryptoService service(boolean encryptionEnabled) {
    ApimProperties properties = new ApimProperties();
    properties.getEncryption().setEnabled(encryptionEnabled);
    properties.getEncryption().setResponseFields(List.of("secret"));
    return new ApimPayloadCryptoService(
        properties,
        MAPPER,
        new ApimPayloadFieldTransformer(),
        new ApimRsaPayloadCrypto(MAPPER),
        new ApimAesKeyFactory(),
        mock(ApimAppCertificateService.class));
  }
}
