package com.bct.ngtpa.apiservice.adapter.out.apim.oauth;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApimTokenService {

    private final WebClient.Builder webClientBuilder;
    private final ApimProperties apimProperties;

    private final ConcurrentHashMap<String, ApimTokenCacheEntry> tokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Mono<ApimTokenCacheEntry>> inFlight = new ConcurrentHashMap<>();

    public Mono<String> getAccessToken(ApimCredentialProfile profile) {
        String profileId = profile.profileId();
        ApimTokenCacheEntry entry = tokenCache.get(profileId);
        long now = Instant.now().toEpochMilli();
        if (entry != null && entry.expiresAtMillis() > now) {
            log.info("Using cached APIM access token for profileId={}", profileId);
            return Mono.just(entry.accessToken());
        }
        return refreshAccessToken(profile).map(ApimTokenCacheEntry::accessToken);
    }

    public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
        String profileId = profile.profileId();
        return inFlight.computeIfAbsent(profileId, id -> Mono.defer(() -> fetchToken(profile))
                .doOnNext(entry -> {
                    tokenCache.put(profileId, entry);
                    log.info("Cached fresh APIM access token for profileId={} expiresAt={}",
                            profileId, Instant.ofEpochMilli(entry.expiresAtMillis()));
                })
                .doFinally(sig -> inFlight.remove(profileId))
                .cache());
    }

    public void evictToken(String profileId) {
        tokenCache.remove(profileId);
        inFlight.remove(profileId);
        log.debug("Evicted token cache for profileId={}", profileId);
    }

    private Mono<ApimTokenCacheEntry> fetchToken(ApimCredentialProfile profile) {
        String tokenUri = profile.tokenUri() != null ? profile.tokenUri() : apimProperties.getOauth().getTokenUri();
        int skewSeconds = apimProperties.getOauth().getTokenRenewalSkewSeconds();

        WebClient client = webClientBuilder.build();
        log.info("Fetching fresh APIM access token for profileId={}", profile.profileId());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", profile.clientId());
        form.add("client_secret", profile.clientSecret());

        return client.post()
                .uri(tokenUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(tr -> {
                    long now = Instant.now().toEpochMilli();
                    long expiresAt = now + (tr.expiresIn * 1000L) - (skewSeconds * 1000L);
                    if (expiresAt <= now) {
                        expiresAt = now + (tr.expiresIn * 1000L);
                    }
                    ApimTokenCacheEntry entry = new ApimTokenCacheEntry(profile.profileId(), tr.accessToken, tr.tokenType, expiresAt, tr.refreshToken);
                    return entry;
                })
                .doOnError(ex -> log.warn("Failed to fetch APIM token for profileId={}, error={}", profile.profileId(), ex.toString()));
    }

    // Token response DTO
    private static class TokenResponse {
        @JsonProperty("access_token")
        String accessToken;
        @JsonProperty("token_type")
        String tokenType;
        @JsonProperty("expires_in")
        int expiresIn;
        @JsonProperty("refresh_token")
        String refreshToken;
    }
}
