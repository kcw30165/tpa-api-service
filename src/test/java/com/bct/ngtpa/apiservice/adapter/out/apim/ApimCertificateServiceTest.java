package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import java.net.URI;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.PublicKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApimCertificateServiceTest {

    @Test
    void fetchesAndCachesCertificate() throws Exception {
        // prepare a real X509 PEM via helper
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        java.security.Security.addProvider(new BouncyCastleProvider());
        ApimCertificateHelper helper = new ApimCertificateHelper(new BouncyCastleProvider());
        var cert = helper.generateCertificate(kp.getPublic(), kp.getPrivate());
        String pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getEncoder().encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----";

        AtomicInteger counter = new AtomicInteger();
        AtomicReference<String> keyIdHeader = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> certificateHeader = new AtomicReference<>();
        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            keyIdHeader.set(req.headers().getFirst("KeyId"));
            authHeader.set(req.headers().getFirst("Authorization"));
            certificateHeader.set(req.headers().getFirst("Certificate"));
            ClientResponse resp = ClientResponse.create(HttpStatus.OK).body(pem).build();
            return Mono.just(resp);
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimCertificateService svc = new ApimCertificateService(builder, props, helper);

        ApimCredentialProfile profile = new ApimCredentialProfile("default", null, null, "akey", null, "http://localhost", List.of(), null);

        var pk1 = svc.getPublicKey(profile).block();
        var pk2 = svc.getPublicKey(profile).block();

        assertEquals(pk1, pk2);
        assertEquals("akey", keyIdHeader.get());
        assertNull(authHeader.get());
        assertNull(certificateHeader.get());
        assertEquals(1, counter.get());

        svc.evictCertificate(profile.profileId());
        svc.getPublicKey(profile).block();
        assertEquals(2, counter.get());
    }

    @Test
    void rejectsMissingApiKeyBeforeCallingRemoteEndpoint() {
        AtomicInteger counter = new AtomicInteger();
        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("unused").build());
        };

        ApimCertificateService svc = new ApimCertificateService(
                WebClient.builder().exchangeFunction(ef),
                new ApimProperties(),
                mock(ApimCertificateHelper.class));

        ApimCredentialProfile profile = new ApimCredentialProfile("default", null, null, null, null, "http://localhost", List.of(), null);

        StepVerifier.create(svc.getPublicKey(profile))
                .expectErrorSatisfies(ex -> assertEquals(
                        "APIM apiKey is required for certificate fetch.",
                        ex.getMessage()))
                .verify();

        assertEquals(0, counter.get());
    }

    @Test
    void getBctPublicKeyUsesLegacyPropertyFallbacks() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        java.security.Security.addProvider(new BouncyCastleProvider());
        ApimCertificateHelper helper = new ApimCertificateHelper(new BouncyCastleProvider());
        var cert = helper.generateCertificate(kp.getPublic(), kp.getPrivate());
        String pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getEncoder().encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----";

        AtomicReference<String> keyIdHeader = new AtomicReference<>();
        AtomicReference<URI> requestUri = new AtomicReference<>();
        ExchangeFunction ef = req -> {
            keyIdHeader.set(req.headers().getFirst("KeyId"));
            requestUri.set(req.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(pem).build());
        };

        ApimProperties props = new ApimProperties();
        props.setBaseUrl("http://example.test/root");
        props.getEncryption().setApiKey("legacy-key");
        props.getEncryption().setCertificatePath("/api/cert");

        ApimCertificateService svc = new ApimCertificateService(WebClient.builder().exchangeFunction(ef), props, helper);

        PublicKey publicKey = svc.getBctPublicKey().block();

        assertArrayEquals(kp.getPublic().getEncoded(), publicKey.getEncoded());
        assertEquals("legacy-key", keyIdHeader.get());
        assertEquals("http://example.test/api/cert", requestUri.get().toString());
    }

    @Test
    void rejectsBlankCertificateResponses() {
        ApimProperties props = new ApimProperties();
        props.getEncryption().setApiKey("akey");

        ExchangeFunction ef = req -> Mono.just(ClientResponse.create(HttpStatus.OK).body("   ").build());
        ApimCertificateService svc = new ApimCertificateService(
                WebClient.builder().exchangeFunction(ef),
                props,
                mock(ApimCertificateHelper.class));

        ApimCredentialProfile profile = new ApimCredentialProfile("default", null, null, null, null, "http://localhost", List.of(), null);

        StepVerifier.create(svc.getPublicKey(profile))
                .expectErrorSatisfies(ex -> assertEquals(
                        "BCT certificate API returned an empty response.",
                        ex.getMessage()))
                .verify();
    }

    @Test
    void expiredCertificatesFallBackToConfiguredTtlCaching() throws Exception {
        PublicKey publicKey = generateKeyPair().getPublic();
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getPublicKey()).thenReturn(publicKey);
        when(certificate.getNotAfter()).thenReturn(Date.from(Instant.now().minusSeconds(30)));

        ApimCertificateHelper helper = mock(ApimCertificateHelper.class);
        when(helper.getX509CertificateFromPem("pem")).thenReturn(certificate);

        AtomicInteger counter = new AtomicInteger();
        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("pem").build());
        };

        ApimProperties props = new ApimProperties();
        props.getEncryption().setApiKey("akey");
        props.getEncryption().setCertificateCacheTtlSeconds(60);

        ApimCertificateService svc = new ApimCertificateService(WebClient.builder().exchangeFunction(ef), props, helper);
        ApimCredentialProfile profile = new ApimCredentialProfile("default", null, null, "akey", null, "http://localhost", List.of(), null);

        assertEquals(publicKey, svc.getPublicKey(profile).block());
        assertEquals(publicKey, svc.getPublicKey(profile).block());
        assertEquals(1, counter.get());
    }

    @Test
    void certificateDateReadFailuresAlsoFallBackToConfiguredTtlCaching() throws Exception {
        PublicKey publicKey = generateKeyPair().getPublic();
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getPublicKey()).thenReturn(publicKey);
        when(certificate.getNotAfter()).thenThrow(new IllegalStateException("broken certificate"));

        ApimCertificateHelper helper = mock(ApimCertificateHelper.class);
        when(helper.getX509CertificateFromPem("pem")).thenReturn(certificate);

        AtomicInteger counter = new AtomicInteger();
        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("pem").build());
        };

        ApimProperties props = new ApimProperties();
        props.getEncryption().setApiKey("akey");
        props.getEncryption().setCertificateCacheTtlSeconds(60);

        ApimCertificateService svc = new ApimCertificateService(WebClient.builder().exchangeFunction(ef), props, helper);
        ApimCredentialProfile profile = new ApimCredentialProfile("default", null, null, "akey", null, "http://localhost", List.of(), null);

        assertEquals(publicKey, svc.getPublicKey(profile).block());
        assertEquals(publicKey, svc.getPublicKey(profile).block());
        assertEquals(1, counter.get());
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }
}
