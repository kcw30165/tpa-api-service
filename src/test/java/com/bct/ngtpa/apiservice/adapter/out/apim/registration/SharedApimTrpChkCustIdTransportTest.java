package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.PublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedApimTrpChkCustIdTransportTest {

    @Test
    void usesSafeBusinessRetryAndRequestIdMetadata() {
        var properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);
        var facade = new CapturingApimWebClientFacade();
        var certificates = new TrackingApimCertificateService();
        var crypto = new StubApimPayloadCryptoService(new ApimResponseEnvelope<>(
                new ApimResponseBody<>(
                        "err.tpa.cust.id.not.registered",
                        List.of(new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "STAFF")))));
        var transport = new SharedApimTrpChkCustIdTransport(facade, certificates, crypto, properties);

        StepVerifier.create(transport.post(new TrpChkCustIdApimRequest("cust-1"), "req-123"))
                .assertNext(result -> {
                    assertThat(result.errMessage()).isEqualTo("err.tpa.cust.id.not.registered");
                    assertThat(result.data()).singleElement().satisfies(item -> {
                        assertThat(item.customerId()).isEqualTo("db01");
                        assertThat(item.userId()).isEqualTo("3010008");
                    });
                })
                .verifyComplete();

        assertThat(facade.path).isEqualTo(SharedApimTrpChkCustIdTransport.API_NAME);
        assertThat(facade.requestBody).isEqualTo(new TrpChkCustIdApimRequest("cust-1"));
        assertThat(facade.retryEligibility).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
        // Strict routing context no longer carries arbitrary request metadata; ensure context is present
        assertThat(facade.context).isNotNull();
        assertThat(certificates.calls).isZero();
        assertThat(crypto.decryptCalls).isEqualTo(1);
    }

    private static final class CapturingApimWebClientFacade extends ApimWebClientFacade {
        private String path;
        private Object requestBody;
        private ApimCredentialResolutionContext context;
        private ApimRetryEligibility retryEligibility;

        private CapturingApimWebClientFacade() {
            super(WebClient.create(), WebClient.builder(), new ObjectMapper(), ignored -> Mono.empty(), null, null,
                    new ApimProperties());
        }

        @Override
        public Mono<String> post(
                String path,
                Object requestBody,
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope scope,
                ApimCredentialResolutionContext resolutionContext,
                ApimRetryEligibility retryEligibility) {
            this.path = path;
            this.requestBody = requestBody;
            this.context = resolutionContext;
            this.retryEligibility = retryEligibility;
            return Mono.just("{\"response\":{}} ");
        }
    }

    private static final class TrackingApimCertificateService extends ApimCertificateService {
        private int calls;

        private TrackingApimCertificateService() {
            super(WebClient.builder(), new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            calls++;
            return Mono.error(new AssertionError("certificate lookup should not run when encryption is disabled"));
        }
    }

    private static final class StubApimPayloadCryptoService extends ApimPayloadCryptoService {
        private final ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope;
        private int decryptCalls;

        private StubApimPayloadCryptoService(ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
            super(new ApimProperties(), new ObjectMapper(), null, null, null, null);
            this.envelope = envelope;
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, PublicKey publicKey) {
            return source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(
                String apiName,
                String responseJson,
                Class<T> dataClass,
                PublicKey publicKey) {
            decryptCalls++;
            return (ApimResponseEnvelope<T>) envelope;
        }
    }
}