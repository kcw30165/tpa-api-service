package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public final class SharedApimTrpChkCustIdTransport implements TrpChkCustIdTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedApimTrpChkCustIdTransport.class);
    public static final String API_NAME = "/ws/NGTPA/v1/TRPChkCustId";

    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties apimProperties;

    public SharedApimTrpChkCustIdTransport(
            ApimWebClientFacade facade,
            ApimCertificateService certificates,
            ApimPayloadCryptoService crypto,
            ApimProperties apimProperties) {
        this.facade = Objects.requireNonNull(facade, "facade must not be null");
        this.certificates = Objects.requireNonNull(certificates, "certificates must not be null");
        this.crypto = Objects.requireNonNull(crypto, "crypto must not be null");
        this.apimProperties = Objects.requireNonNull(apimProperties, "apimProperties must not be null");
    }

    @Override
    public Mono<TrpChkCustIdApimEnvelope> post(TrpChkCustIdApimRequest request, String correlationId) {
        Objects.requireNonNull(request, "request must not be null");
        if (!apimProperties.getEncryption().isEnabled()) {
            return postEncrypted(request, correlationId, null, null);
        }
        var ctx = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);
        return facade.resolveProfile(com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT, ctx)
                        .flatMap(profile -> certificates.getPublicKey(profile)
                        .flatMap(publicKey -> {
                            Object encryptedRequest = crypto.encryptRequest(API_NAME, request, TrpChkCustIdApimRequest.class, publicKey);
                            return facade.post(API_NAME, encryptedRequest, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                                    .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpChkCustIdApimDataItem.class, publicKey))
                                    .map(this::toEnvelopePreservingBusinessErrMessage)
                                    .onErrorMap(ApimCryptoException.class, this::toApimException)
                                    .doOnError(error -> LOGGER.error(
                                            "event=forgot-password.username-lookup.transport-failed requestId={} exceptionType={}",
                                            correlationId, error.getClass().getName(), error));
                        }));
    }

    private Mono<TrpChkCustIdApimEnvelope> postEncrypted(
            TrpChkCustIdApimRequest request,
            String correlationId,
            PublicKey publicKey,
            ApimCredentialProfile profile) {
        Object encryptedRequest = crypto.encryptRequest(API_NAME, request, TrpChkCustIdApimRequest.class, publicKey);
        Map<String, String> metadata = correlationId == null || correlationId.isBlank()
                ? Map.of()
                : Map.of("requestId", correlationId);
        var context = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);
        if (profile != null) {
            return facade.post(API_NAME, encryptedRequest, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                    .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpChkCustIdApimDataItem.class, publicKey))
                    .map(this::toEnvelopePreservingBusinessErrMessage)
                    .onErrorMap(ApimCryptoException.class, this::toApimException)
                    .doOnError(error -> LOGGER.error(
                            "event=forgot-password.username-lookup.transport-failed requestId={} exceptionType={}",
                            correlationId, error.getClass().getName(), error));
        }
        return facade.post(API_NAME, encryptedRequest, com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT, context, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpChkCustIdApimDataItem.class, publicKey))
                .map(this::toEnvelopePreservingBusinessErrMessage)
                .onErrorMap(ApimCryptoException.class, this::toApimException)
                .doOnError(error -> LOGGER.error(
                        "event=forgot-password.username-lookup.transport-failed requestId={} exceptionType={}",
                        correlationId, error.getClass().getName(), error));
    }

    /**
     * Public helper that invokes TRPChkCustId using the ACCOUNT_ENV resolver.
     * This is used by registration username-check (workflow-specific) which must
     * route by protected selectedTrust.accountEnv. The transport remains shared
     * but the caller supplies the safe accountEnv value (server-owned).
     */
    public Mono<TrpChkCustIdApimEnvelope> postWithAccountEnv(
            TrpChkCustIdApimRequest request,
            String correlationId,
            String accountEnv) {
        Objects.requireNonNull(accountEnv, "accountEnv must not be null");
        var ctx = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, accountEnv);
        return facade.resolveProfile(com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.ACCOUNT_ENV, ctx)
                .flatMap(profile -> certificates.getPublicKey(profile)
                .flatMap(publicKey -> {
                    Object encryptedRequest = crypto.encryptRequest(API_NAME, request, TrpChkCustIdApimRequest.class, publicKey);
                    return facade.post(API_NAME, encryptedRequest, profile, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                            .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpChkCustIdApimDataItem.class, publicKey))
                            .map(this::toEnvelopePreservingBusinessErrMessage)
                            .onErrorMap(ApimCryptoException.class, this::toApimException)
                            .doOnError(error -> LOGGER.error(
                                    "event=registration.username-check.transport-failed requestId={} exceptionType={}",
                                    correlationId, error.getClass().getName(), error));
                }));
    }

    private TrpChkCustIdApimEnvelope toEnvelopePreservingBusinessErrMessage(
            ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
        if (envelope == null || envelope.getResponse() == null) {
            throw new IllegalStateException("TRPChkCustId response envelope is missing response");
        }
        String errMessage = envelope.getResponse().getErrMessage();
        List<TrpChkCustIdApimDataItem> data = envelope.getResponse().getData();
        int dataCount = data == null ? 0 : data.size();
        LOGGER.info("event=forgot-password.username-lookup.transport responsePresent=true errMessagePresent={} dataCount={}",
                errMessage != null && !errMessage.isBlank(), dataCount);

        // Validate minimal successful response invariants:
        // - If errMessage is blank/empty and there's no data, treat as invalid upstream response
        // - If data is present, ensure at least one non-null item with meaningful fields exists
        boolean errMessagePresent = errMessage != null && !errMessage.isBlank();
        boolean dataPresent = data != null && !data.isEmpty();
        if (!errMessagePresent && !dataPresent) {
            // Invalid/malformed upstream payload — classify as APIM response invalid
            throw new com.bct.ngtpa.apiservice.exception.ApimException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    com.bct.ngtpa.apiservice.shared.error.ErrorCodes.APIM_RESPONSE_INVALID,
                    "TRPChkCustId returned an invalid/malformed response");
        }
        if (dataPresent) {
            boolean anyValidItem = data.stream().anyMatch(item -> item != null && (item.customerId() != null || item.isExist() != null));
            if (!anyValidItem) {
                throw new com.bct.ngtpa.apiservice.exception.ApimException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        com.bct.ngtpa.apiservice.shared.error.ErrorCodes.APIM_RESPONSE_INVALID,
                        "TRPChkCustId returned data items but they are malformed");
            }
        }

        return new TrpChkCustIdApimEnvelope(errMessage, data);
    }

    private ApimException toApimException(ApimCryptoException exception) {
        return new ApimException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.SYSTEM_UNEXPECTED,
                exception.getMessage(),
                exception);
    }
}