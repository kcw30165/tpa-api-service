package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
