package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.config.TrpMemberLoginProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public final class SharedApimTrpMemberLoginTransport implements TrpMemberLoginTransport {
    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties apimProperties;
    private final TrpMemberLoginProperties loginProperties;

    public SharedApimTrpMemberLoginTransport(
            ApimWebClientFacade facade,
            ApimCertificateService certificates,
            ApimPayloadCryptoService crypto,
            ApimProperties apimProperties,
            TrpMemberLoginProperties loginProperties) {
        this.facade = Objects.requireNonNull(facade, "facade must not be null");
        this.certificates = Objects.requireNonNull(certificates, "certificates must not be null");
        this.crypto = Objects.requireNonNull(crypto, "crypto must not be null");
        this.apimProperties = Objects.requireNonNull(apimProperties, "apimProperties must not be null");
        this.loginProperties = Objects.requireNonNull(loginProperties, "loginProperties must not be null");
    }

    @Override
    public Mono<ApimResponseEnvelope<TrpMemberLoginApimDataItem>> post(
            TrpMemberLoginApimRequest request) {
        if (!apimProperties.getEncryption().isEnabled()) {
            return postEncrypted(request, null);
        }
        return certificates.getBctPublicKey()
                .flatMap(publicKey -> postEncrypted(request, publicKey));
    }

    private Mono<ApimResponseEnvelope<TrpMemberLoginApimDataItem>> postEncrypted(
            TrpMemberLoginApimRequest request,
            PublicKey publicKey) {
        String path = requireText(loginProperties.getPath(), "apim.login.member-login.path");
        String operation = requireText(
                loginProperties.getOperationName(),
                "apim.login.member-login.operation-name");
        var encryptedRequest = crypto.encryptRequest(
                path,
                request,
                TrpMemberLoginApimRequest.class,
                publicKey);
        var context = new ApimCredentialResolutionContext(
                null, null, null, null, null, null, null, operation, Map.of());
        return facade.post(path, encryptedRequest, context, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                .map(body -> crypto.decryptResponseEnvelope(
                        path,
                        body,
                        TrpMemberLoginApimDataItem.class,
                        publicKey))
                .onErrorMap(ApimCryptoException.class, this::cryptoFailure);
    }

    private ApimException cryptoFailure(ApimCryptoException exception) {
        return new ApimException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.SYSTEM_UNEXPECTED,
                "TRPMemberLogin crypto contract failure",
                exception);
    }

    private String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must not be blank");
        }
        return value.trim();
    }
}
