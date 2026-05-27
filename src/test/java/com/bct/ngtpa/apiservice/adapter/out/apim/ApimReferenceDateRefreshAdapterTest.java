package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimRequest;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApimReferenceDateRefreshAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void postsToWebSysDateEndpointWithUnchangedEnvAndReturnsSysDate() throws Exception {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("""
                {
                  "response": {
                    "err-message": "",
                    "data": [
                      {
                        "sys-date": "31/12/2025"
                      }
                    ]
                  }
                }
                """);

        ApimReferenceDateRefreshAdapter adapter = new ApimReferenceDateRefreshAdapter(
                facade,
                new NoopApimCertificateService(),
                new PassThroughEnvelopePayloadCryptoService(),
                disabledEncryptionProperties());

        LocalDate result = adapter.fetchReferenceDate("JP").block();

        assertEquals(1, facade.postInvocationCount);
        assertEquals("/ws/NGTPA/v1/TRPGetWebSysDate", facade.capturedPath);
        assertEquals("JP", facade.capturedRequest.getAccountEnv());

        String json = OBJECT_MAPPER.writeValueAsString(facade.capturedRequest);
        assertEquals(LocalDate.of(2025, 12, 31), result);
        assertFalse(json.toLowerCase().contains("datadomain"));
        assertEquals("{\"env\":\"JP\"}", json);
    }

    @Test
    void usesCertificateFlowWhenEncryptionEnabled() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);
        FixedEnvelopePayloadCryptoService payloadCryptoService = new FixedEnvelopePayloadCryptoService(responseEnvelope(
                "",
                List.of(GetWebSysDateApimDataItem.builder().sysDate("31/12/2025").build())));
        FixedCertificateService certificateService = new FixedCertificateService(new TestPublicKey("bct-public"));
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("ignored");
        ApimReferenceDateRefreshAdapter adapter = new ApimReferenceDateRefreshAdapter(
                facade,
                certificateService,
                payloadCryptoService,
                properties);

        LocalDate result = adapter.fetchReferenceDate("JP").block();

        assertEquals(LocalDate.of(2025, 12, 31), result);
        assertEquals(1, certificateService.invocationCount);
        assertSame(certificateService.publicKey, payloadCryptoService.lastPublicKey);
    }

    @Test
    void surfacesApimTopLevelErrorMessageAsBadGateway() {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("""
                {
                  "response": {
                    "err-message": "APIM sys date failed",
                    "data": []
                  }
                }
                """);

        ApimReferenceDateRefreshAdapter adapter = new ApimReferenceDateRefreshAdapter(
                facade,
                new NoopApimCertificateService(),
                new PassThroughEnvelopePayloadCryptoService(),
                disabledEncryptionProperties());

        ApimException ex = assertThrows(ApimException.class, () -> adapter.fetchReferenceDate("JP").block());

        assertEquals("APIM sys date failed", ex.getMessage());
        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
    }

    @Test
    void throwsWhenPayloadMissing() {
        ApimReferenceDateRefreshAdapter adapter = new ApimReferenceDateRefreshAdapter(
                null,
                null,
                null,
                new ApimProperties());

        ApimException ex = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toReferenceDate", new Object[] {null}));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
        assertEquals("APIM response payload is missing.", ex.getMessage());
    }

    @Test
    void throwsWhenDataMissing() {
        ApimReferenceDateRefreshAdapter adapter = new ApimReferenceDateRefreshAdapter(
                null,
                null,
                null,
                new ApimProperties());

        ApimException ex = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toReferenceDate", responseEnvelope("", List.of())));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
        assertEquals("APIM response data is missing.", ex.getMessage());
    }

    @Test
    void throwsWhenSysDateMissingBlankOrInvalid() {
        ApimReferenceDateRefreshAdapter adapter = new ApimReferenceDateRefreshAdapter(
                null,
                null,
                null,
                new ApimProperties());

        ApimException missing = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        adapter,
                        "toReferenceDate",
                        responseEnvelope("", List.of(GetWebSysDateApimDataItem.builder().build()))));
        ApimException blank = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        adapter,
                        "toReferenceDate",
                        responseEnvelope("", List.of(GetWebSysDateApimDataItem.builder().sysDate("   ").build()))));
        ApimException invalid = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        adapter,
                        "toReferenceDate",
                        responseEnvelope("", List.of(GetWebSysDateApimDataItem.builder().sysDate("2025-12-31").build()))));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, missing.getErrorCode());
        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, blank.getErrorCode());
        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, invalid.getErrorCode());
        assertEquals("APIM sys-date is missing.", missing.getMessage());
        assertEquals("APIM sys-date is missing.", blank.getMessage());
        assertEquals("APIM sys-date must use dd/MM/yyyy format.", invalid.getMessage());
    }

    private static ApimProperties disabledEncryptionProperties() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);
        return properties;
    }

    private static ApimResponseEnvelope<GetWebSysDateApimDataItem> responseEnvelope(
            String errMessage,
            List<GetWebSysDateApimDataItem> items) {
        return ApimResponseEnvelope.<GetWebSysDateApimDataItem>builder()
                .response(ApimResponseBody.<GetWebSysDateApimDataItem>builder()
                        .errMessage(errMessage)
                        .data(items)
                        .build())
                .build();
    }

    private static final class CapturingApimWebClientFacade extends ApimWebClientFacade {
        private final String responseBody;
        private String capturedPath;
        private GetWebSysDateApimRequest capturedRequest;
        private int postInvocationCount;

        private CapturingApimWebClientFacade(String responseBody) {
            super(null, OBJECT_MAPPER);
            this.responseBody = responseBody;
        }

        @Override
        public Mono<String> post(String path, Object requestBody) {
            this.capturedPath = path;
            this.capturedRequest = (GetWebSysDateApimRequest) requestBody;
            this.postInvocationCount++;
            return Mono.just(responseBody);
        }
    }

    private static final class PassThroughEnvelopePayloadCryptoService extends ApimPayloadCryptoService {

        private PassThroughEnvelopePayloadCryptoService() {
            super(new ApimProperties(), OBJECT_MAPPER, null, null, null, null);
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, PublicKey publicKey) {
            return source;
        }

        @Override
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(String apiName, String responseJson, Class<T> dataClass,
                PublicKey publicKey) {
            try {
                return OBJECT_MAPPER.readValue(
                        responseJson,
                        OBJECT_MAPPER.getTypeFactory().constructParametricType(ApimResponseEnvelope.class, dataClass));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final class FixedEnvelopePayloadCryptoService extends ApimPayloadCryptoService {
        private final ApimResponseEnvelope<GetWebSysDateApimDataItem> envelope;
        private PublicKey lastPublicKey;

        private FixedEnvelopePayloadCryptoService(ApimResponseEnvelope<GetWebSysDateApimDataItem> envelope) {
            super(new ApimProperties(), OBJECT_MAPPER, null, null, null, null);
            this.envelope = envelope;
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, PublicKey publicKey) {
            this.lastPublicKey = publicKey;
            return source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(String apiName, String responseJson, Class<T> dataClass,
                PublicKey publicKey) {
            this.lastPublicKey = publicKey;
            return (ApimResponseEnvelope<T>) envelope;
        }
    }

    private static final class NoopApimCertificateService extends ApimCertificateService {
        private NoopApimCertificateService() {
            super(WebClient.builder(), new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            throw new AssertionError("Certificate service should not be called when encryption is disabled");
        }
    }

    private static final class FixedCertificateService extends ApimCertificateService {
        private final PublicKey publicKey;
        private int invocationCount;

        private FixedCertificateService(PublicKey publicKey) {
            super(WebClient.builder(), new ApimProperties(), null);
            this.publicKey = publicKey;
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            invocationCount++;
            return Mono.just(publicKey);
        }
    }

    private static final class TestPublicKey implements PublicKey {
        private final String value;

        private TestPublicKey(String value) {
            this.value = value;
        }

        @Override
        public String getAlgorithm() {
            return "RSA";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return value.getBytes();
        }
    }
}