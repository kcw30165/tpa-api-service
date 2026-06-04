package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApimUpdatePersonalInformationAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void postsToUpdateMemberInfoEndpointAndMapsSuccess() {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("""
                {
                  "response": {
                    "err-message": "",
                    "data": [
                      { "success": true }
                    ]
                  }
                }
                """);
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(
                facade,
                new NoopApimCertificateService(),
                new PassThroughApimPayloadCryptoService(),
                disabledEncryptionProperties());

        UpdatePersonalInformationResult result = adapter.updateMemberInfo(command()).block();

        assertEquals(1, facade.postInvocationCount);
        assertEquals("/ws/NGTPA/v1/TRPUpdMemberInfo", facade.capturedPath);
        assertEquals("POL-001", facade.capturedRequest.getPolicyNo());
        assertEquals("CERT-001", facade.capturedRequest.getCertNo());
        assertEquals("JP", facade.capturedRequest.getAccountEnv());
        assertEquals("actor-user", facade.capturedRequest.getUserId());
        assertEquals("98765432", facade.capturedRequest.getUpdateFields().get("mobile-number"));
        assertTrue(result.success());
    }

    @Test
    void surfacesApimTopLevelErrorMessageAsBadGateway() {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("""
                {
                  "response": {
                    "err-message": "APIM update failed",
                    "data": []
                  }
                }
                """);
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(
                facade,
                new NoopApimCertificateService(),
                new PassThroughApimPayloadCryptoService(),
                disabledEncryptionProperties());

        ApimException ex = assertThrows(ApimException.class, () -> adapter.updateMemberInfo(command()).block());

        assertEquals("APIM update failed", ex.getMessage());
        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
    }

    @Test
    void treatsFalseSuccessAsBadGateway() {
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(null, null, null, new ApimProperties());

        ApimException ex = assertThrows(ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toResult", responseEnvelope(List.of(
                        UpdateMemberInfoApimDataItem.builder().success(false).build()))));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
        assertEquals("APIM personal information update was not successful.", ex.getMessage());
    }

    @Test
    void treatsMissingDataAsBadGateway() {
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(null, null, null, new ApimProperties());

        ApimException ex = assertThrows(ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toResult", responseEnvelope(List.of())));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
        assertEquals("APIM personal information update was not successful.", ex.getMessage());
    }

    @Test
    void throwsWhenPayloadMissing() {
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(null, null, null, new ApimProperties());

        ApimException ex = assertThrows(ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toResult", (Object) null));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, ex.getErrorCode());
        assertEquals("APIM response payload is missing.", ex.getMessage());
    }

    @Test
    void usesCertificateFlowWhenEncryptionEnabled() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);
        FixedEnvelopePayloadCryptoService payloadCryptoService = new FixedEnvelopePayloadCryptoService(responseEnvelope(List.of(
                UpdateMemberInfoApimDataItem.builder().success(true).build())));
        FixedCertificateService certificateService = new FixedCertificateService(new TestPublicKey("bct-public"));
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("ignored");
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(
                facade,
                certificateService,
                payloadCryptoService,
                properties);

        UpdatePersonalInformationResult result = adapter.updateMemberInfo(command()).block();

        assertEquals(1, certificateService.invocationCount);
        assertSame(certificateService.publicKey, payloadCryptoService.lastPublicKey);
        assertTrue(result.success());
    }

    @Test
    void cryptoFailureIsMappedToApimException() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(
                new CapturingApimWebClientFacade("ignored"),
                new FailingCertificateService(),
                new FixedEnvelopePayloadCryptoService(responseEnvelope(List.of())),
                properties);

        ApimException ex = assertThrows(ApimException.class, () -> adapter.updateMemberInfo(command()).block());

        assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, ex.getErrorCode());
        assertEquals("Certificate crypto error", ex.getMessage());
    }

    private static UpdateMemberInfoCommand command() {
        Map<String, Object> updateFields = new LinkedHashMap<>();
        updateFields.put("mobile-number", "98765432");
        updateFields.put("email", "user@example.com");
        return new UpdateMemberInfoCommand("JP", "POL-001", "CERT-001", "actor-user", updateFields);
    }

    private static ApimProperties disabledEncryptionProperties() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);
        return properties;
    }

    private static ApimResponseEnvelope<UpdateMemberInfoApimDataItem> responseEnvelope(
            List<UpdateMemberInfoApimDataItem> items) {
        return ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder()
                .response(ApimResponseBody.<UpdateMemberInfoApimDataItem>builder()
                        .errMessage("")
                        .data(items)
                        .build())
                .build();
    }

    private static final class CapturingApimWebClientFacade extends ApimWebClientFacade {
        private final String responseBody;
        private String capturedPath;
        private UpdateMemberInfoApimRequest capturedRequest;
        private int postInvocationCount;

        private CapturingApimWebClientFacade(String responseBody) {
            super(null, OBJECT_MAPPER);
            this.responseBody = responseBody;
        }

        @Override
        public Mono<String> post(String path, Object requestBody) {
            this.capturedPath = path;
            this.capturedRequest = (UpdateMemberInfoApimRequest) requestBody;
            this.postInvocationCount++;
            return Mono.just(responseBody);
        }
    }

    private static final class PassThroughApimPayloadCryptoService extends ApimPayloadCryptoService {
        private PassThroughApimPayloadCryptoService() {
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
                var root = OBJECT_MAPPER.readTree(responseJson);
                var responseNode = root.get("response");
                String errMessage = responseNode == null || responseNode.get("err-message") == null
                        ? null
                        : responseNode.get("err-message").asText();
                java.util.List<T> items = new java.util.ArrayList<>();
                if (responseNode != null && responseNode.get("data") != null && responseNode.get("data").isArray()) {
                    for (var itemNode : responseNode.get("data")) {
                        items.add(OBJECT_MAPPER.treeToValue(itemNode, dataClass));
                    }
                }
                return ApimResponseEnvelope.<T>builder()
                        .response(ApimResponseBody.<T>builder()
                                .errMessage(errMessage)
                                .data(items)
                                .build())
                        .build();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final class FixedEnvelopePayloadCryptoService extends ApimPayloadCryptoService {
        private final ApimResponseEnvelope<UpdateMemberInfoApimDataItem> envelope;
        private PublicKey lastPublicKey;

        private FixedEnvelopePayloadCryptoService(ApimResponseEnvelope<UpdateMemberInfoApimDataItem> envelope) {
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

    private static final class FixedCertificateService extends ApimCertificateService {
        private final PublicKey publicKey;
        private int invocationCount;

        private FixedCertificateService(PublicKey publicKey) {
            super(null, new ApimProperties(), null);
            this.publicKey = publicKey;
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            invocationCount++;
            return Mono.just(publicKey);
        }
    }

    private static final class NoopApimCertificateService extends ApimCertificateService {
        private NoopApimCertificateService() {
            super(null, new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            return Mono.error(new AssertionError("Certificate lookup should not be called when encryption is disabled."));
        }
    }

    private static final class FailingCertificateService extends ApimCertificateService {
        private FailingCertificateService() {
            super(null, new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            return Mono.error(new ApimCryptoException("Certificate crypto error"));
        }
    }

    private record TestPublicKey(String value) implements PublicKey {
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
