package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import java.util.Objects;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

/**
 * Shared transport implementation that owns strict profile-aware execution for TRPSearchMember.
 */
@Component
public final class SharedApimTrpSearchMemberTransport implements TrpSearchMemberTransport {

    public static final String API_NAME = "/ws/NGTPA/v1/TRPSearchMember";

    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties apim;

    public SharedApimTrpSearchMemberTransport(ApimWebClientFacade facade,
            ApimCertificateService certificates,
            ApimPayloadCryptoService crypto,
            ApimProperties apim) {
        this.facade = Objects.requireNonNull(facade);
        this.certificates = Objects.requireNonNull(certificates);
        this.crypto = Objects.requireNonNull(crypto);
        this.apim = Objects.requireNonNull(apim);
    }

    @Override
    public Mono<ApimResponseEnvelope<com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem>> post(
            com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest request,
            String correlationId) {
        String path = API_NAME;
        var ctx = new ApimCredentialResolutionContext(com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null);
        if (!apim.getEncryption().isEnabled()) {
            // non-encrypted path: let facade resolve profile for NON_ACCOUNT
            return facade.post(path, request, ApimDatabaseScope.NON_ACCOUNT, ctx, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                    .map(body -> crypto.decryptResponseEnvelope(path, body,
                            com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem.class, null));
        }
        // encrypted path: resolve profile and fetch profile certificate, then encrypt with that key and post using same profile
        return facade.resolveProfile(ApimDatabaseScope.NON_ACCOUNT, ctx)
                .flatMap(profile -> certificates.getPublicKey(profile)
                        .flatMap(key -> {
                            Object encrypted = crypto.encryptRequest(path, request,
                                    com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest.class, key);
                            return facade.post(path, encrypted, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                                    .map(body -> crypto.decryptResponseEnvelope(path, body,
                                            com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem.class, key));
                        }));
    }
}
