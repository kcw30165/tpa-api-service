package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ApimCertificateService {

    private final WebClient.Builder webClientBuilder;
    private final ApimProperties apimProperties;
    private final ApimCertificateHelper apimCertificateHelper;

    private final ConcurrentHashMap<String, CertificateCacheEntry> certCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Mono<CertificateCacheEntry>> inFlight = new ConcurrentHashMap<>();

    public ApimCertificateService(
            WebClient.Builder webClientBuilder,
            ApimProperties apimProperties,
            ApimCertificateHelper apimCertificateHelper) {
        this.webClientBuilder = webClientBuilder;
        this.apimProperties = apimProperties;
        this.apimCertificateHelper = apimCertificateHelper;
    }

    public Mono<PublicKey> getBctPublicKey() {
        // Legacy behavior: build a profile from single-profile properties and delegate
        ApimProperties.Oauth o = apimProperties.getOauth();
        ApimProperties.Encryption e = apimProperties.getEncryption();
        ApimCredentialProfile profile = new ApimCredentialProfile(
                "default",
                o.getClientId(),
                o.getClientSecret(),
                e.getApiKey(),
                o.getTokenUri(),
                apimProperties.getBaseUrl(),
                o.getScope(),
                e.getCertificatePath()
        );
        return getPublicKey(profile);
    }

    public Mono<PublicKey> getPublicKey(ApimCredentialProfile profile) {
        String pid = profile.profileId();
        CertificateCacheEntry entry = certCache.get(pid);
        long now = Instant.now().toEpochMilli();
        if (entry != null && entry.expiresAtMillis > now) {
            return Mono.just(entry.publicKey);
        }
        return refreshPublicKey(profile);
    }

    public Mono<PublicKey> refreshPublicKey(ApimCredentialProfile profile) {
        String pid = profile.profileId();
        Mono<CertificateCacheEntry> request = inFlight.computeIfAbsent(pid, id -> Mono.defer(() -> fetchCertificateEntry(profile))
            .doOnNext(entry -> log.debug("Fetched certificate for profileId={}", pid))
            .doOnNext(entry -> certCache.put(pid, entry))
                .doFinally(sig -> inFlight.remove(pid))
                .cache());
        return request.map(entry -> entry.publicKey);
    }

    public void evictCertificate(String profileId) {
        certCache.remove(profileId);
        inFlight.remove(profileId);
        log.debug("Evicted certificate cache for profileId={}", profileId);
    }

    private Mono<CertificateCacheEntry> fetchCertificateEntry(ApimCredentialProfile profile) {
        String apiKey = profile.apiKey() != null ? profile.apiKey() : apimProperties.getEncryption().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return Mono.error(new ApimCryptoException("APIM apiKey is required for certificate fetch."));
        }

        String baseUrl = profile.baseUrl() != null ? profile.baseUrl() : apimProperties.getBaseUrl();
        String certificatePath = profile.certificatePath() != null ? profile.certificatePath() : apimProperties.getEncryption().getCertificatePath();

        WebClient client = webClientBuilder.clone()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .build();

        return client.get()
                .uri(UriComponentsBuilder.fromUriString(baseUrl)
                        .replacePath(certificatePath)
                        .replaceQuery(null)
                        .build(true)
                        .toUri())
                .header("KeyId", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(
                        new ApimCryptoException("BCT certificate API returned an empty response.")))
                .map(pem -> {
                    X509Certificate cert = apimCertificateHelper.getX509CertificateFromPem(pem);
                    return new CertificateCacheEntry(cert.getPublicKey(), calculateExpiryMillis(cert));
                });
    }

    private long calculateExpiryMillis(X509Certificate cert) {
        try {
            long notAfter = cert.getNotAfter().toInstant().toEpochMilli();
            int skewSeconds = apimProperties.getEncryption().getCertificateRenewalSkewSeconds();
            long expiry = notAfter - (skewSeconds * 1000L);
            if (expiry <= Instant.now().toEpochMilli()) {
                long ttl = apimProperties.getEncryption().getCertificateCacheTtlSeconds();
                return Instant.now().toEpochMilli() + ttl * 1000L;
            }
            return expiry;
        } catch (Exception ex) {
            int ttl = apimProperties.getEncryption().getCertificateCacheTtlSeconds();
            return Instant.now().toEpochMilli() + ttl * 1000L;
        }
    }

    private static final class CertificateCacheEntry {
        final PublicKey publicKey;
        final long expiresAtMillis;

        CertificateCacheEntry(PublicKey publicKey, long expiresAtMillis) {
            this.publicKey = publicKey;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
