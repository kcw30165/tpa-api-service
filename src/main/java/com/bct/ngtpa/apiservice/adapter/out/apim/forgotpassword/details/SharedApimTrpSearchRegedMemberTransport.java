package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;

import com.bct.ngtpa.apiservice.adapter.out.apim.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import java.lang.reflect.Method;
import java.security.PublicKey;
import java.util.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class SharedApimTrpSearchRegedMemberTransport implements TrpSearchRegedMemberTransport {
    public static final String API_NAME = "/ws/NGTPA/v1/TRPSearchRegedMember";
    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties properties;

    public SharedApimTrpSearchRegedMemberTransport(ApimWebClientFacade facade, ApimCertificateService certificates,
            ApimPayloadCryptoService crypto, ApimProperties properties) {
        this.facade = Objects.requireNonNull(facade);
        this.certificates = Objects.requireNonNull(certificates);
        this.crypto = Objects.requireNonNull(crypto);
        this.properties = Objects.requireNonNull(properties);
    }

    public Mono<TrpSearchRegedMemberEnvelope> post(TrpSearchRegedMemberRequest request, String requestId) {
        Objects.requireNonNull(request);
        if (!properties.getEncryption().isEnabled()) {
            return post(request, requestId);
        }
        var ctx = new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext(ActorUserRole.ROLE_USER, null);
        return facade.resolveProfile(com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT, ctx)
                .flatMap(profile -> certificates.getPublicKey(profile)
                        .flatMap(key -> {
                            Object encrypted = crypto.encryptRequest(API_NAME, request, TrpSearchRegedMemberRequest.class, key);
                            return facade.post(API_NAME, encrypted, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                                    .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpSearchRegedMemberDataItem.class, key))
                                    .map(this::envelope);
                        }));
    }

    private TrpSearchRegedMemberEnvelope envelope(ApimResponseEnvelope<TrpSearchRegedMemberDataItem> value) {
        Object response = invoke(value, "getResponse", "response"), source = response == null ? value : response;
        Object err = invoke(source, "getErrMessage", "errMessage", "getErrmessage", "errmessage");
        Object data = invoke(source, "getData", "data");
        @SuppressWarnings("unchecked")
        List<TrpSearchRegedMemberDataItem> items = data instanceof List<?> l ? (List<TrpSearchRegedMemberDataItem>) l
                : List.of();
        return new TrpSearchRegedMemberEnvelope(err == null ? "" : String.valueOf(err), items);
    }

    private Object invoke(Object target, String... names) {
        if (target == null)
            return null;
        for (String n : names)
            try {
                Method m = target.getClass().getMethod(n);
                return m.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        return null;
    }
}
