package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApimPersonalInformationAdapterTest {

    @Test
    void fetchMemberInfo_uses_disabled_encryption_flow_and_parses_response() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);

        // Build a fake APIM envelope containing config and data
        MemberInfoConfigItem cfg = MemberInfoConfigItem.builder().itemId("addr1").configValue("EDITABLE_COM").build();
        Map<String, Object> dataRecord = new HashMap<>();
        dataRecord.put("addr1", "1 Example Street");

        GetMemberInfoDataItem dataItem = GetMemberInfoDataItem.builder()
                .config(List.of(cfg))
                .data(List.of(dataRecord))
                .build();

        ApimResponseEnvelope<GetMemberInfoDataItem> envelope = ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
                .response(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.<GetMemberInfoDataItem>builder()
                        .data(List.of(dataItem))
                        .build())
                .build();

        FixedEnvelopePayloadCryptoService payloadCryptoService = new FixedEnvelopePayloadCryptoService(envelope);
        FixedBodyApimWebClientFacade facade = new FixedBodyApimWebClientFacade();

        ApimMemberInfoAdapter adapter = new ApimMemberInfoAdapter(facade, new RejectingCertificateService(), payloadCryptoService, properties);

        FetchMemberInfoCommand cmd = new FetchMemberInfoCommand("JP", "policy", "cert", "user");
        MemberInfoResult result = adapter.fetchMemberInfo(cmd).block();

        assertNotNull(result);
        assertEquals("1 Example Street", ((Map<?, ?>) result.getPayload().get("data")).get("addr1"));
        assertEquals("EDITABLE_COM", ((Map<?, ?>) result.getPayload().get("config")).get("addr1"));
        assertEquals(1, facade.postInvocationCount);
        assertEquals("/ws/NGTPA/v1/TRPGetMemberInfo", facade.capturedPath);
    }

    @Test
    void fetchMemberInfo_uses_certificate_when_encryption_enabled() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);

        MemberInfoConfigItem cfg = MemberInfoConfigItem.builder().itemId("addr1").configValue("READONLY").build();
        Map<String, Object> dataRecord = new HashMap<>();
        dataRecord.put("addr1", "X");

        GetMemberInfoDataItem dataItem = GetMemberInfoDataItem.builder()
                .config(List.of(cfg))
                .data(List.of(dataRecord))
                .build();

        ApimResponseEnvelope<GetMemberInfoDataItem> envelope = ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
                .response(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.<GetMemberInfoDataItem>builder()
                        .data(List.of(dataItem))
                        .build())
                .build();

        FixedEnvelopePayloadCryptoService payloadCryptoService = new FixedEnvelopePayloadCryptoService(envelope);
        FixedBodyApimWebClientFacade facade = new FixedBodyApimWebClientFacade();
        CountingCertificateService certificateService = new CountingCertificateService(new TestPublicKey("bct-public"));

        ApimMemberInfoAdapter adapter = new ApimMemberInfoAdapter(facade, certificateService, payloadCryptoService, properties);

        FetchMemberInfoCommand cmd = new FetchMemberInfoCommand("JP", "policy", "cert", "user");
        MemberInfoResult result = adapter.fetchMemberInfo(cmd).block();

        assertNotNull(result);
        assertEquals(1, certificateService.invocationCount);
        assertEquals(1, facade.postInvocationCount);
    }

    // --- Test helpers ---

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
        private final ApimResponseEnvelope<GetMemberInfoDataItem> envelope;

        private FixedEnvelopePayloadCryptoService(ApimResponseEnvelope<GetMemberInfoDataItem> envelope) {
            super(new com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties(), new ObjectMapper(), null, null, null, null);
            this.envelope = envelope;
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, java.security.PublicKey publicKey) {
            return source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(String apiName, String responseJson, Class<T> dataClass, java.security.PublicKey publicKey) {
            return (ApimResponseEnvelope<T>) envelope;
        }
    }

    private static final class CountingCertificateService extends ApimCertificateService {
        private final java.security.PublicKey publicKey;
        private int invocationCount;

        private CountingCertificateService(java.security.PublicKey publicKey) {
            super(null, new com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties(), null);
            this.publicKey = publicKey;
        }

        @Override
        public reactor.core.publisher.Mono<java.security.PublicKey> getBctPublicKey() {
            invocationCount++;
            return reactor.core.publisher.Mono.just(publicKey);
        }
    }

    private static final class RejectingCertificateService extends ApimCertificateService {
        private RejectingCertificateService() {
            super(null, new com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties(), null);
        }

        @Override
        public reactor.core.publisher.Mono<java.security.PublicKey> getBctPublicKey() {
            return reactor.core.publisher.Mono.error(new AssertionError("Certificate lookup should not run when encryption is disabled."));
        }
    }

    private record TestPublicKey(String value) implements java.security.PublicKey {
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
