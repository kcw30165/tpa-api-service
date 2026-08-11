package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

/**
 * Minimal routing context required for APIM profile resolution.
 * Must contain exactly two components per Stage 004 contract.
 */
public record ApimCredentialResolutionContext(
        ActorUserRole actorUserRole,
        String accountEnv) {
}
