package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimRequest;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApimNotificationReadStatusAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void postsToUpdateReadStatusEndpointAndMapsApimItems() {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("""
                {
                  "response": {
                    "err-message": "",
                    "response": {
                      "err-message": "",
                      "data": [
                        {
                          "msg-code": "",
                          "msg-code-long": "msgCode1",
                          "success": true
                        },
                        {
                          "msg-code": "",
                          "msg-code-long": "msgCode2",
                          "success": false
                        }
                      ]
                    }
                  }
                }
                """);

        ApimNotificationReadStatusAdapter adapter = new ApimNotificationReadStatusAdapter(
                facade,
                new NoopApimCertificateService(),
                new PassThroughApimPayloadCryptoService(),
                disabledEncryptionProperties());

        UpdateNotificationsReadStatusResult result = adapter.updateReadStatus(command()).block();

        assertEquals(1, facade.postInvocationCount);
        assertEquals("/ws/NGTPA/v1/TRPUpdMsgRead", facade.capturedPath);
        assertEquals("msgCode1,msgCode2", facade.capturedRequest.getMsgCodeLong());
        assertEquals("READ", facade.capturedRequest.getStatus());
        assertEquals("2", facade.capturedRequest.getCertNo());
        assertEquals(2, result.notifications().size());
        assertEquals("msgCode1", result.notifications().getFirst().msgCode());
        assertTrue(result.notifications().getFirst().isRead());
        assertEquals("msgCode2", result.notifications().get(1).msgCode());
        assertFalse(result.notifications().get(1).isRead());
    }

    @Test
    void surfacesApimTopLevelErrorMessageAsBadGateway() {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade("""
                {
                  "response": {
                    "err-message": "APIM update failed",
                    "response": {
                      "err-message": "",
                      "data": []
                    }
                  }
                }
                """);

        ApimNotificationReadStatusAdapter adapter = new ApimNotificationReadStatusAdapter(
                facade,
                new NoopApimCertificateService(),
                new PassThroughApimPayloadCryptoService(),
                disabledEncryptionProperties());

        ApimException ex = assertThrows(ApimException.class, () -> adapter.updateReadStatus(command()).block());

        assertEquals("APIM update failed", ex.getMessage());
        assertEquals("502", ex.getErrorCode());
    }

    private static UpdateNotificationsReadStatusCommand command() {
        return new UpdateNotificationsReadStatusCommand(
                "DEV",
                "MBR",
                List.of("msgCode1", "msgCode2"),
                "",
          "2",
                "",
                "01/01/2024",
                MessageStatus.READ);
    }

    private static ApimProperties disabledEncryptionProperties() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);
        return properties;
    }

    private static final class CapturingApimWebClientFacade extends ApimWebClientFacade {
        private final String responseBody;
        private String capturedPath;
        private UpdateNotificationReadStatusApimRequest capturedRequest;
        private int postInvocationCount;

        private CapturingApimWebClientFacade(String responseBody) {
            super(null, OBJECT_MAPPER);
            this.responseBody = responseBody;
        }

        @Override
        public Mono<String> post(String path, Object requestBody) {
            this.capturedPath = path;
            this.capturedRequest = (UpdateNotificationReadStatusApimRequest) requestBody;
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
        public <T> T decryptResponse(String apiName, String responseJson, Class<T> targetType, PublicKey publicKey) {
            try {
                return OBJECT_MAPPER.readValue(responseJson, targetType);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
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
}