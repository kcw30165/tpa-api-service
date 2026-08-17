package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import com.bct.ngtpa.apiservice.adapter.out.apim.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config.TrpGetAccountListProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import java.security.PublicKey;
import java.util.*;
import reactor.core.publisher.Mono;

public final class SharedApimTrpGetAccountListTransport implements TrpGetAccountListTransport {
    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties apim;
    private final TrpGetAccountListProperties properties;

    public SharedApimTrpGetAccountListTransport(ApimWebClientFacade facade, ApimCertificateService certificates,
            ApimPayloadCryptoService crypto, ApimProperties apim, TrpGetAccountListProperties properties) {
        this.facade = Objects.requireNonNull(facade);
        this.certificates = Objects.requireNonNull(certificates);
        this.crypto = Objects.requireNonNull(crypto);
        this.apim = Objects.requireNonNull(apim);
        this.properties = Objects.requireNonNull(properties);
        properties.validate();
    }

    public Mono<ApimResponseEnvelope<TrpGetAccountListApimDataItem>> post(TrpGetAccountListApimRequest request,
            String correlationId) {
        if (!apim.getEncryption().isEnabled()) {
            return postEncrypted(request, correlationId, null, null);
        }
        var ctx = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);
        String path = properties.getPath().trim();
        return facade.resolveProfile(ApimDatabaseScope.NON_ACCOUNT, ctx)
                        .flatMap(profile -> certificates.getPublicKey(profile)
                        .flatMap(key -> {
                            Object encrypted = crypto.encryptRequest(path, request, TrpGetAccountListApimRequest.class, key);
                            return facade.post(path, encrypted, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                                    .map(body -> crypto.decryptResponseEnvelope(path, body, TrpGetAccountListApimDataItem.class, key));
                        }));
    }

    private Mono<ApimResponseEnvelope<TrpGetAccountListApimDataItem>> postEncrypted(
            TrpGetAccountListApimRequest request, String correlationId, PublicKey key, ApimCredentialProfile profile) {
        String path = properties.getPath().trim();
        String operation = properties.getOperationName().trim();
        Object encrypted = crypto.encryptRequest(path, request, TrpGetAccountListApimRequest.class, key);
        Map<String, String> metadata = correlationId == null ? Map.of() : Map.of("requestId", correlationId);
        var context = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);
        if (profile != null) {
            return facade.post(path, encrypted, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                    .map(body -> crypto.decryptResponseEnvelope(path, body, TrpGetAccountListApimDataItem.class, key));
        }
        return facade.post(path, encrypted, ApimDatabaseScope.NON_ACCOUNT, context, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                .map(body -> crypto.decryptResponseEnvelope(path, body, TrpGetAccountListApimDataItem.class, key));
    }
}
