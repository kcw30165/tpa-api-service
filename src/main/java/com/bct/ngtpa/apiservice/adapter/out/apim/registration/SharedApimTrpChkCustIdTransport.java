package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
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
        return apimProperties.getEncryption().isEnabled()
                ? certificates.getBctPublicKey().flatMap(key -> postEncrypted(request, correlationId, key))
                : postEncrypted(request, correlationId, null);
    }

    private Mono<TrpChkCustIdApimEnvelope> postEncrypted(
            TrpChkCustIdApimRequest request,
            String correlationId,
            PublicKey publicKey) {
        Object encryptedRequest = crypto.encryptRequest(API_NAME, request, TrpChkCustIdApimRequest.class, publicKey);
        Map<String, String> metadata = correlationId == null || correlationId.isBlank()
                ? Map.of()
                : Map.of("requestId", correlationId);
        var context = new ApimCredentialResolutionContext(null, null, null, null, null, null, null, API_NAME, metadata);
        return facade.post(API_NAME, encryptedRequest, context, ApimRetryEligibility.SAFE_BUSINESS_CALL)
                .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpChkCustIdApimDataItem.class, publicKey))
                .map(this::toEnvelopePreservingBusinessErrMessage)
                .onErrorMap(ApimCryptoException.class, this::toApimException)
                .doOnError(error -> LOGGER.error(
                        "event=forgot-password.username-lookup.transport-failed requestId={} exceptionType={}",
                        correlationId, error.getClass().getName(), error));
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