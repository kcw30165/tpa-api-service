package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
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
import java.lang.reflect.Method;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public final class SharedApimTrpChkCustIdTransport implements TrpChkCustIdTransport {
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
                .onErrorMap(ApimCryptoException.class, this::toApimException);
    }

    private TrpChkCustIdApimEnvelope toEnvelopePreservingBusinessErrMessage(
            ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
        String errMessage = resolveErrMessage(envelope);
        List<TrpChkCustIdApimDataItem> data = resolveData(envelope);
        return new TrpChkCustIdApimEnvelope(errMessage, data);
    }

    private String resolveErrMessage(ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
        Object response = invokeAny(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object value = invokeAny(source, "getErrMessage", "errMessage", "getErrmessage", "errmessage");
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<TrpChkCustIdApimDataItem> resolveData(ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
        Object response = invokeAny(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object value = invokeAny(source, "getData", "data");
        if (value instanceof List<?> list) {
            return (List<TrpChkCustIdApimDataItem>) list;
        }
        try {
            return ApimResponseValidator.requireSuccessData(envelope);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Object invokeAny(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private ApimException toApimException(ApimCryptoException exception) {
        return new ApimException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.SYSTEM_UNEXPECTED,
                exception.getMessage(),
                exception);
    }
}