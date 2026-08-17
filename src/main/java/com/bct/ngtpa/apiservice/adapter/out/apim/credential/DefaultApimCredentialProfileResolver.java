package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Strict resolver: resolves by explicit routing configuration per Stage 004.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class DefaultApimCredentialProfileResolver implements ApimCredentialProfileResolver {

    private final ApimProperties apimProperties;

    @Override
    public Mono<ApimCredentialProfile> resolve(ApimDatabaseScope scope, ApimCredentialResolutionContext context) {
        if (scope == null || context == null) {
            return Mono.error(new ApimProfileResolutionException("scope and context are required"));
        }
        if (context.actorUserRole() == null) {
            return Mono.error(new ApimProfileResolutionException("actorUserRole is required"));
        }
        if (context.actorUserRole() != ActorUserRole.ROLE_USER_MBR && context.actorUserRole() != ActorUserRole.ROLE_INPUT) {
            return Mono.error(new ApimProfileResolutionException("actor role unsupported"));
        }

        ApimProperties.Routing routing = apimProperties.getRouting();
        switch (scope) {
            case NON_ACCOUNT -> {
                String nonAccountProfileId = routing.getNonAccountProfileId();
                if (nonAccountProfileId == null || nonAccountProfileId.isBlank()) {
                    return Mono.error(new ApimProfileResolutionException("non-account profile not configured"));
                }
                return findProfileById(nonAccountProfileId);
            }
            case ACCOUNT_ENV -> {
                String env = context.accountEnv();
                if (env == null || env.isBlank()) {
                    return Mono.error(new ApimProfileResolutionException("accountEnv is required for ACCOUNT_ENV scope"));
                }
                String normalized = env.trim().toUpperCase(java.util.Locale.ROOT);
                String mappedProfile = routing.getAccountEnvProfileMappings().get(normalized);
                if (mappedProfile == null) {
                    return Mono.error(new ApimProfileResolutionException("no mapping for accountEnv"));
                }
                return findProfileById(mappedProfile);
            }
            default -> {
                return Mono.error(new ApimProfileResolutionException("unsupported scope"));
            }
        }
    }

    private Mono<ApimCredentialProfile> findProfileById(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Mono.error(new ApimProfileResolutionException("blank profileId"));
        }
        ApimProperties.CredentialProfiles cp = apimProperties.getCredentialProfiles();
        if (cp.getProfiles() != null) {
            for (ApimProperties.CredentialProfiles.Profile p : cp.getProfiles()) {
                if (profileId.equals(p.getProfileId())) {
                    log.debug("Resolved APIM profileId={}", p.getProfileId());
                    return Mono.just(toRecord(p));
                }
            }
        }
        return Mono.error(new ApimProfileResolutionException("referenced profile not found"));
    }

    private ApimCredentialProfile toRecord(ApimProperties.CredentialProfiles.Profile p) {
        return new ApimCredentialProfile(
                p.getProfileId(),
                p.getClientId(),
                p.getClientSecret(),
                p.getApiKey(),
                p.getTokenUri(),
                p.getBaseUrl(),
                p.getScope(),
                p.getCertificatePath());
    }

}
