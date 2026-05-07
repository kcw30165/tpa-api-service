package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Default resolver: returns the configured default profile.
 * TODO: replace with trust-based mapping when mapping rules are confirmed.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class DefaultApimCredentialProfileResolver implements ApimCredentialProfileResolver {

    private final ApimProperties apimProperties;

    @Override
    public Mono<ApimCredentialProfile> resolve(ApimCredentialResolutionContext context) {
        ApimProperties.CredentialProfiles cp = apimProperties.getCredentialProfiles();
        // Prefer explicit profiles if configured
        if (cp.getProfiles() != null && !cp.getProfiles().isEmpty()) {
            String defaultId = cp.getDefaultProfileId();
            for (ApimProperties.CredentialProfiles.Profile p : cp.getProfiles()) {
                if (p.getProfileId() != null && p.getProfileId().equals(defaultId)) {
                    // Do not log secrets
                    log.debug("Resolved APIM profileId={}", p.getProfileId());
                    return Mono.just(toRecord(p));
                }
            }
            // If defaultProfileId not found, return first profile
            ApimProperties.CredentialProfiles.Profile first = cp.getProfiles().get(0);
            log.warn("Configured defaultProfileId={} not found; using first profileId={}", cp.getDefaultProfileId(), first.getProfileId());
            return Mono.just(toRecord(first));
        }

        // Legacy single-profile fallback
        ApimProperties.Oauth o = apimProperties.getOauth();
        ApimProperties.Encryption e = apimProperties.getEncryption();
        ApimCredentialProfile profile = new ApimCredentialProfile(
                cp.getDefaultProfileId(),
                o.getClientId(),
                o.getClientSecret(),
                e.getApiKey(),
                o.getTokenUri(),
                apimProperties.getBaseUrl(),
                o.getScope(),
                e.getCertificatePath()
        );
        log.debug("Resolved legacy default APIM profileId={}", profile.profileId());
        return Mono.just(profile);
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
                p.getCertificatePath()
        );
    }
}
