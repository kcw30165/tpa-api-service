package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.security.PublicKey;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedApimTrpSearchMemberTransportTest {

        private ApimWebClientFacade facade;
        private ApimCertificateService certificates;
        private ApimPayloadCryptoService crypto;
        private ApimProperties properties;
        private SharedApimTrpSearchMemberTransport transport;

        @BeforeEach
        void setUp() {
                facade = mock(ApimWebClientFacade.class);
                certificates = mock(ApimCertificateService.class);
                crypto = mock(ApimPayloadCryptoService.class);
                properties = new ApimProperties();
                properties.getEncryption().setEnabled(false);
                transport = new SharedApimTrpSearchMemberTransport(facade, certificates, crypto, properties);
        }

        @Test
        void nonEncryptedPathUsesStrictNonAccountRoutingAndSafeRetry() {
                TrpSearchMemberApimRequest request = request();
                ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope = envelope();
                when(facade.post(
                                eq(SharedApimTrpSearchMemberTransport.API_NAME),
                                eq(request),
                                eq(ApimDatabaseScope.NON_ACCOUNT),
                                any(ApimCredentialResolutionContext.class),
                                eq(ApimRetryEligibility.SAFE_BUSINESS_CALL)))
                                .thenReturn(Mono.just("{}"));
                when(crypto.decryptResponseEnvelope(
                                eq(SharedApimTrpSearchMemberTransport.API_NAME),
                                eq("{}"),
                                eq(TrpSearchMemberApimDataItem.class),
                                eq(null)))
                                .thenReturn(envelope);

                StepVerifier.create(transport.post(request, "req-009"))
                                .expectNext(envelope)
                                .verifyComplete();

                ArgumentCaptor<ApimCredentialResolutionContext> contextCaptor = ArgumentCaptor
                                .forClass(ApimCredentialResolutionContext.class);
                verify(facade).post(
                                eq(SharedApimTrpSearchMemberTransport.API_NAME),
                                eq(request),
                                eq(ApimDatabaseScope.NON_ACCOUNT),
                                contextCaptor.capture(),
                                eq(ApimRetryEligibility.SAFE_BUSINESS_CALL));
                assertThat(contextCaptor.getValue().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER);
                assertThat(contextCaptor.getValue().accountEnv()).isNull();
                verify(facade, never()).resolveProfile(any(), any());
                verify(certificates, never()).getPublicKey(any(ApimCredentialProfile.class));
        }

        @Test
        void encryptedPathUsesOneResolvedProfileForCertificateAndPost() {
                properties.getEncryption().setEnabled(true);
                TrpSearchMemberApimRequest request = request();
                ApimCredentialProfile profile = mock(ApimCredentialProfile.class);
                PublicKey key = mock(PublicKey.class);
                ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope = envelope();
                TrpSearchMemberApimRequest encrypted = new TrpSearchMemberApimRequest(
                                "STAFFNO", "encrypted-payload", "01/01/2000", "encrypted@example.invalid", null);

                when(facade.resolveProfile(
                                eq(ApimDatabaseScope.NON_ACCOUNT),
                                any(ApimCredentialResolutionContext.class)))
                                .thenReturn(Mono.just(profile));
                when(certificates.getPublicKey(profile)).thenReturn(Mono.just(key));
                when(crypto.encryptRequest(
                                SharedApimTrpSearchMemberTransport.API_NAME,
                                request,
                                TrpSearchMemberApimRequest.class,
                                key))
                                .thenReturn(encrypted);
                when(facade.post(
                                SharedApimTrpSearchMemberTransport.API_NAME,
                                encrypted,
                                profile,
                                ApimRetryEligibility.SAFE_BUSINESS_CALL))
                                .thenReturn(Mono.just("{}"));
                when(crypto.decryptResponseEnvelope(
                                SharedApimTrpSearchMemberTransport.API_NAME,
                                "{}",
                                TrpSearchMemberApimDataItem.class,
                                key))
                                .thenReturn(envelope);

                StepVerifier.create(transport.post(request, "req-009"))
                                .expectNext(envelope)
                                .verifyComplete();

                ArgumentCaptor<ApimCredentialResolutionContext> contextCaptor = ArgumentCaptor
                                .forClass(ApimCredentialResolutionContext.class);
                verify(facade, times(1)).resolveProfile(
                                eq(ApimDatabaseScope.NON_ACCOUNT), contextCaptor.capture());
                assertThat(contextCaptor.getValue().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER);
                assertThat(contextCaptor.getValue().accountEnv()).isNull();
                verify(certificates, times(1)).getPublicKey(profile);
                verify(facade, times(1)).post(
                                SharedApimTrpSearchMemberTransport.API_NAME,
                                encrypted,
                                profile,
                                ApimRetryEligibility.SAFE_BUSINESS_CALL);
        }

        @Test
        void resolutionFailureHasZeroCertificateEncryptionAndPostSideEffects() {
                properties.getEncryption().setEnabled(true);
                RuntimeException failure = new RuntimeException("resolution failed");
                when(facade.resolveProfile(
                                eq(ApimDatabaseScope.NON_ACCOUNT),
                                any(ApimCredentialResolutionContext.class)))
                                .thenReturn(Mono.error(failure));

                StepVerifier.create(transport.post(request(), "req-009"))
                                .expectErrorMatches(error -> error == failure)
                                .verify();

                verify(certificates, never()).getPublicKey(any(ApimCredentialProfile.class));
                verify(crypto, never()).encryptRequest(any(), any(), any(), any());
                verify(facade, never()).post(
                                any(String.class), any(), any(ApimCredentialProfile.class),
                                any(ApimRetryEligibility.class));
        }

        private static TrpSearchMemberApimRequest request() {
                return new TrpSearchMemberApimRequest(
                                "STAFFNO", "3011825", "15/07/1969", "member@example.com", null);
        }

        private static ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope() {
                TrpSearchMemberApimDataItem item = new TrpSearchMemberApimDataItem(
                                "DB", "DBA", "OE", "00000008802", "5", "", "", true, "db02");
                return new ApimResponseEnvelope<>(new ApimResponseBody<>("", List.of(item)));
        }
}
