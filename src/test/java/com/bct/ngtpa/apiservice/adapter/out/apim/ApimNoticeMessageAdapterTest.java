package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardMessageItem;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApimNoticeMessageAdapterTest {

    private final ApimNoticeMessageAdapter adapter = new ApimNoticeMessageAdapter(
            null, null, null, new ApimProperties());

    @Test
    void mapsApimFieldsToDomainModelUsingCategoryAndStatus() {
        GetMessageBoardMessageItem messageItem = GetMessageBoardMessageItem.builder()
                .msgCode("SHORT-001")
                .msgCodeLong("LONG-001")
                .seq(7)
                .msgCate("IMP_NOTE")
                .msgTitle("Should be ignored")
                .msgContentChi("chi")
                .msgContentEng("eng")
                .startDatetime("29/04/2026 14:15:00")
                .msgStatus("R")
                .isRead(false)
                .build();

        ApimResponseEnvelope<GetMessageBoardDataItem> response = ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
                .response(ApimResponseBody.<GetMessageBoardDataItem>builder()
                        .data(List.of(GetMessageBoardDataItem.builder()
                                .page(1)
                                .size(20)
                                .message(List.of(messageItem))
                                .build()))
                        .build())
                .build();

        NotificationListResult result = (NotificationListResult) ReflectionTestUtils.invokeMethod(
                adapter, "toNotificationListResult", response);

        assertEquals(1, result.notifications().size());
        assertEquals("SHORT-001", result.notifications().getFirst().msgCode());
        assertEquals("LONG-001", result.notifications().getFirst().msgCodeLong());
        assertEquals("IMP_NOTE", result.notifications().getFirst().category());
        assertEquals("IMPORTANT NOTICE", result.notifications().getFirst().msgTitle());
        assertTrue(result.notifications().getFirst().isRead());
    }

        @Test
        void fetchNotificationsUsesDisabledEncryptionFlow() {
                ApimProperties properties = new ApimProperties();
                properties.getEncryption().setEnabled(false);
                FixedEnvelopePayloadCryptoService payloadCryptoService = new FixedEnvelopePayloadCryptoService(messageEnvelope(messageItem("IMP_NOTE", "R", "29/04/2026 14:15:00")));
                FixedBodyApimWebClientFacade facade = new FixedBodyApimWebClientFacade();
                ApimNoticeMessageAdapter flowAdapter = new ApimNoticeMessageAdapter(
                                facade,
                                new RejectingCertificateService(),
                                payloadCryptoService,
                                properties);

                NotificationListResult result = flowAdapter.fetchNotifications(command()).block();

                assertEquals(1, facade.postInvocationCount);
                assertEquals("/ws/NGTPA/v1/TRPGetMsgBoard", facade.capturedPath);
                assertEquals(1, result.notifications().size());
                assertNull(payloadCryptoService.lastPublicKey);
        }

        @Test
        void fetchNotificationsUsesCertificateWhenEncryptionEnabled() {
                ApimProperties properties = new ApimProperties();
                properties.getEncryption().setEnabled(true);
                FixedEnvelopePayloadCryptoService payloadCryptoService = new FixedEnvelopePayloadCryptoService(messageEnvelope(messageItem("ACT_REQ", "U", "29/04/2026 14:15:00")));
                FixedBodyApimWebClientFacade facade = new FixedBodyApimWebClientFacade();
                CountingCertificateService certificateService = new CountingCertificateService(new TestPublicKey("bct-public"));
                ApimNoticeMessageAdapter flowAdapter = new ApimNoticeMessageAdapter(facade, certificateService, payloadCryptoService, properties);

                NotificationListResult result = flowAdapter.fetchNotifications(command()).block();

                assertEquals(1, certificateService.invocationCount);
                assertSame(certificateService.publicKey, payloadCryptoService.lastPublicKey);
                assertEquals(1, result.notifications().size());
                assertFalse(result.notifications().getFirst().isRead());
        }

        @Test
        void parsesApimDateTimeWithoutSeconds() {
                LocalDateTime parsed = (LocalDateTime) ReflectionTestUtils.invokeMethod(
                                adapter, "parseDateTime", "11/10/2023 00:00");

                assertNotNull(parsed);
                assertEquals(LocalDateTime.of(2023, 10, 11, 0, 0), parsed);
        }

    @Test
    void doesNotIncludePageOrSizeInApimRequestPayload() throws Exception {
        GetNotificationsCommand command = new GetNotificationsCommand(
                "DEV", "MBR", 1, 20, "dd/MM/yyyy HH:mm", "Asia/Hong_Kong", "P1", "C1", "U1", "29/04/2026");

        GetMessageBoardApimRequest request = (GetMessageBoardApimRequest) ReflectionTestUtils.invokeMethod(
                adapter, "toApimRequest", command);
        String json = new ObjectMapper().writeValueAsString(request);

        assertTrue(json.contains("\"env\":\"DEV\""));
        assertTrue(json.contains("\"mbr-type\":\"MBR\""));
        assertFalse(json.contains("page"));
        assertFalse(json.contains("size"));
    }

        @Test
        void returnsEmptyResultWhenDataIsMissing() {
                ApimResponseEnvelope<GetMessageBoardDataItem> response = ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
                                .response(ApimResponseBody.<GetMessageBoardDataItem>builder().data(List.of()).build())
                                .build();

                NotificationListResult result = (NotificationListResult) ReflectionTestUtils.invokeMethod(
                                adapter, "toNotificationListResult", response);

                assertTrue(result.notifications().isEmpty());
        }

        @Test
        void throwsWhenPayloadMissingOrTopLevelErrorPresent() {
                ApimException missingPayload = assertThrows(ApimException.class,
                                () -> ReflectionTestUtils.invokeMethod(adapter, "toNotificationListResult", new Object[] {null}));

                ApimResponseEnvelope<GetMessageBoardDataItem> response = ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
                                .response(ApimResponseBody.<GetMessageBoardDataItem>builder().errMessage("APIM failed").build())
                                .build();
                ApimException topLevelError = assertThrows(ApimException.class,
                                () -> ReflectionTestUtils.invokeMethod(adapter, "toNotificationListResult", response));

                assertEquals("APIM response payload is missing.", missingPayload.getMessage());
                assertEquals("APIM failed", topLevelError.getMessage());
        }

        @Test
        void ignoresNullItemsAndTreatsMalformedDatetimeAsNull() {
                GetMessageBoardMessageItem malformedItem = messageItem("custom-type", "U", "bad-date");
                ApimResponseEnvelope<GetMessageBoardDataItem> response = ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
                                .response(ApimResponseBody.<GetMessageBoardDataItem>builder()
                                .data(new ArrayList<>(List.of(
                                        GetMessageBoardDataItem.builder().message(null).build(),
                                        GetMessageBoardDataItem.builder().message(new ArrayList<>(List.of(malformedItem))).build())))
                                                .build())
                                .build();
                response.getResponse().getData().addFirst(null);
                response.getResponse().getData().get(2).getMessage().addFirst(null);

                NotificationListResult result = (NotificationListResult) ReflectionTestUtils.invokeMethod(
                                adapter, "toNotificationListResult", response);

                assertEquals(1, result.notifications().size());
                assertEquals("custom-type", result.notifications().getFirst().msgTitle());
                assertNull(result.notifications().getFirst().startDatetime());
                assertFalse(result.notifications().getFirst().isRead());
        }

        private static GetNotificationsCommand command() {
                return new GetNotificationsCommand("DEV", "MBR", 1, 20, "dd/MM/yyyy HH:mm", "Asia/Hong_Kong", "P1", "C1", "U1", "29/04/2026");
        }

        private static GetMessageBoardMessageItem messageItem(String category, String status, String startDatetime) {
                return GetMessageBoardMessageItem.builder()
                                .msgCode("SHORT-001")
                                .msgCodeLong("LONG-001")
                                .seq(7)
                                .msgCate(category)
                                .msgContentChi("chi")
                                .msgContentEng("eng")
                                .startDatetime(startDatetime)
                                .msgStatus(status)
                                .build();
        }

        private static ApimResponseEnvelope<GetMessageBoardDataItem> messageEnvelope(GetMessageBoardMessageItem messageItem) {
                return ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
                                .response(ApimResponseBody.<GetMessageBoardDataItem>builder()
                                                .data(List.of(GetMessageBoardDataItem.builder().message(List.of(messageItem)).build()))
                                                .build())
                                .build();
        }

        private static final class FixedBodyApimWebClientFacade extends ApimWebClientFacade {
                private String capturedPath;
                private Object capturedRequest;
                private int postInvocationCount;

                private FixedBodyApimWebClientFacade() {
                        super(null, new ObjectMapper());
                }

                @Override
                public Mono<String> post(String path, Object requestBody) {
                        this.capturedPath = path;
                        this.capturedRequest = requestBody;
                        this.postInvocationCount++;
                        return Mono.just("ignored");
                }
        }

        private static final class FixedEnvelopePayloadCryptoService extends ApimPayloadCryptoService {
                private final ApimResponseEnvelope<GetMessageBoardDataItem> envelope;
                private PublicKey lastPublicKey;

                private FixedEnvelopePayloadCryptoService(ApimResponseEnvelope<GetMessageBoardDataItem> envelope) {
                        super(new ApimProperties(), new ObjectMapper(), null, null, null, null);
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

        private static final class CountingCertificateService extends ApimCertificateService {
                private final PublicKey publicKey;
                private int invocationCount;

                private CountingCertificateService(PublicKey publicKey) {
                        super(null, new ApimProperties(), null);
                        this.publicKey = publicKey;
                }

                @Override
                public Mono<PublicKey> getBctPublicKey() {
                        invocationCount++;
                        return Mono.just(publicKey);
                }
        }

        private static final class RejectingCertificateService extends ApimCertificateService {
                private RejectingCertificateService() {
                        super(null, new ApimProperties(), null);
                }

                @Override
                public Mono<PublicKey> getBctPublicKey() {
                        return Mono.error(new AssertionError("Certificate lookup should not run when encryption is disabled."));
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