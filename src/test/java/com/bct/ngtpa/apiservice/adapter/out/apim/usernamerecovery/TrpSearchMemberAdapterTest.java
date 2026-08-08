package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class TrpSearchMemberAdapterTest {

    private static final String PATH = "/ws/NGTPA/v1/TRPSearchMember";

    private final ApimProperties properties = new ApimProperties();
    private CapturingApimWebClientFacade facade;
    private TrackingApimCertificateService certificates;
    private StubApimPayloadCryptoService crypto;
    private TrpSearchMemberAdapter adapter;

    @BeforeEach
    void setUp() {
        properties.getEncryption().setEnabled(false);
        facade = new CapturingApimWebClientFacade();
        certificates = new TrackingApimCertificateService();
        crypto = new StubApimPayloadCryptoService(envelope("", List.of(registeredItem("DB", "DBA", "db02"))));
        adapter = new TrpSearchMemberAdapter(facade, certificates, crypto, properties);
    }

    @Test
    void matchedEnvelopeMapsToMatchedResultAndPropagatesOnlyRequestIdWithSafeRetry() {
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
                    assertThat(result.candidates()).singleElement().satisfies(candidate -> {
                        assertThat(candidate.accountEnv()).isEqualTo("DB");
                        assertThat(candidate.trustCode()).isEqualTo("DBA");
                        assertThat(candidate.registered()).isTrue();
                        assertThat(candidate.userName()).isEqualTo("db02");
                    });
                })
                .verifyComplete();

        assertThat(facade.path).isEqualTo(PATH);
        assertThat(facade.requestBody).isInstanceOf(TrpSearchMemberApimRequest.class);
        TrpSearchMemberApimRequest request = (TrpSearchMemberApimRequest) facade.requestBody;
        assertThat(request.searchBy()).isEqualTo("STAFFNO");
        assertThat(request.searchValue()).isEqualTo("3011825");
        assertThat(facade.retryEligibility).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
        assertThat(facade.context.requestMetadata()).containsEntry("requestId", "req-009");
        assertThat(certificates.calls).isZero();
        assertThat(crypto.decryptCalls).isEqualTo(1);
    }

    @Test
    void memberNotFoundErrorMessageMapsToNotFound() {
        crypto = new StubApimPayloadCryptoService(envelope("err.tpa.member.not.found", List.of()));
        adapter = new TrpSearchMemberAdapter(facade, certificates, crypto, properties);

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.NOT_FOUND))
                .verifyComplete();
    }

    @Test
    void blankSuccessWithEmptyDataMapsToNotFound() {
        crypto = new StubApimPayloadCryptoService(envelope("", List.of()));
        adapter = new TrpSearchMemberAdapter(facade, certificates, crypto, properties);

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.NOT_FOUND))
                .verifyComplete();
    }

    @Test
    void contractViolationMapsToContractInvalid() {
        crypto = new StubApimPayloadCryptoService(envelope("", List.of(registeredItem("DB", "DBA", ""))));
        adapter = new TrpSearchMemberAdapter(facade, certificates, crypto, properties);

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID))
                .verifyComplete();
    }

    @Test
    void nullEnvelopeOrNullResponseMapsToUnavailable() {
        crypto = new StubApimPayloadCryptoService(new ApimResponseEnvelope<>(null));
        adapter = new TrpSearchMemberAdapter(facade, certificates, crypto, properties);

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void unclassifiableErrorMessageMapsToUnknownOutcomeWithoutLeakingDownstreamCode() {
        crypto = new StubApimPayloadCryptoService(envelope("err.tpa.bad.request", List.of()));
        adapter = new TrpSearchMemberAdapter(facade, certificates, crypto, properties);

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> {
                    assertThat(result.status())
                            .isEqualTo(UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME);
                    assertThat(result.toString()).doesNotContain("err.tpa.bad.request");
                })
                .verifyComplete();
    }

    @Test
    void apimExceptionMapsToUnavailable() {
        facade.response = Mono.error(new ApimException(
                HttpStatus.BAD_GATEWAY, "APIM request failed with status=502 body=..."));

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void connectionFailureMapsToUnavailable() {
        facade.response = Mono.error(new WebClientRequestException(
                new RuntimeException("connection refused"), HttpMethod.POST, URI.create(PATH), HttpHeaders.EMPTY));

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void timeoutMapsToUnavailable() {
        facade.response = Mono.error(new TimeoutException("timed out"));

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void decryptionFailureMapsToUnavailable() {
        crypto.decryptFailure = new ApimCryptoException("Failed to decrypt APIM response payload.");

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void unrecognizedRuntimeExceptionIsNotSwallowed() {
        facade.response = Mono.error(new IllegalStateException("unexpected"));

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void encryptionEnabledUsesCertificatePublicKey() throws Exception {
        PublicKey key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        certificates.key = key;
        properties.getEncryption().setEnabled(true);

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED))
                .verifyComplete();

        assertThat(certificates.calls).isEqualTo(1);
        assertThat(crypto.encryptKeys).containsExactly(key);
        assertThat(crypto.decryptKeys).containsExactly(key);
    }

    @Test
    void resultNeverDisclosesIdentityDobOrContact() {
        facade.response = Mono.error(new ApimException(
                HttpStatus.BAD_GATEWAY, "APIM request failed with status=502 body=..."));

        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.toString())
                        .doesNotContain("3011825", "15/07/1969", "member@example.com"))
                .verifyComplete();
    }

    private static Context context(String requestId) {
        return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, requestId);
    }

    private static UsernameRecoveryLookupCommand command() {
        return new UsernameRecoveryLookupCommand(
                MemberIdentityType.STAFFNO, "3011825", "15/07/1969", "member@example.com", null);
    }

    private static ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope(
            String error, List<TrpSearchMemberApimDataItem> data) {
        return new ApimResponseEnvelope<>(new ApimResponseBody<>(error, data));
    }

    private static TrpSearchMemberApimDataItem registeredItem(String env, String trustCode, String custId) {
        return new TrpSearchMemberApimDataItem(
                env, trustCode, "OE", "00000008802", "5", "", "", true, custId);
    }

    private static final class CapturingApimWebClientFacade extends ApimWebClientFacade {
        private String path;
        private Object requestBody;
        private ApimCredentialResolutionContext context;
        private ApimRetryEligibility retryEligibility;
        private Mono<String> response = Mono.just("{\"response\":{}}");

        private CapturingApimWebClientFacade() {
            super(WebClient.create(), WebClient.builder(), new ObjectMapper(), ignored -> Mono.empty(), null, null,
                    new ApimProperties());
        }

        @Override
        public Mono<String> post(String path, Object requestBody,
                ApimCredentialResolutionContext resolutionContext, ApimRetryEligibility retryEligibility) {
            this.path = path;
            this.requestBody = requestBody;
            this.context = resolutionContext;
            this.retryEligibility = retryEligibility;
            return response;
        }
    }

    private static final class TrackingApimCertificateService extends ApimCertificateService {
        private int calls;
        private PublicKey key;

        private TrackingApimCertificateService() {
            super(WebClient.builder(), new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            calls++;
            return key == null
                    ? Mono.error(new AssertionError("certificate lookup should not run when encryption is disabled"))
                    : Mono.just(key);
        }
    }

    private static final class StubApimPayloadCryptoService extends ApimPayloadCryptoService {
        private final ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope;
        private final List<PublicKey> encryptKeys = new ArrayList<>();
        private final List<PublicKey> decryptKeys = new ArrayList<>();
        private int decryptCalls;
        private RuntimeException decryptFailure;

        private StubApimPayloadCryptoService(ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope) {
            super(new ApimProperties(), new ObjectMapper(), null, null, null, null);
            this.envelope = envelope;
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, PublicKey publicKey) {
            encryptKeys.add(publicKey);
            return source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(
                String apiName, String responseJson, Class<T> dataClass, PublicKey publicKey) {
            decryptCalls++;
            decryptKeys.add(publicKey);
            if (decryptFailure != null) {
                throw decryptFailure;
            }
            return (ApimResponseEnvelope<T>) envelope;
        }
    }
}
