package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimRegistrationLookupAdapter implements RegistrationLookupPort {
    private static final String API_NAME = "/ws/NGTPA/v1/TRPSearchUnregMember";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;
    private final ApimRegistrationLookupTransport legacyTestTransport;
    private final TrpSearchUnregMemberApimMapper requestMapper;
    private final TrpSearchUnregMemberApimResultMapper resultMapper;

    @Autowired
    public ApimRegistrationLookupAdapter(
            ApimWebClientFacade apimWebClientFacade,
            ApimCertificateService apimCertificateService,
            ApimPayloadCryptoService apimPayloadCryptoService,
            ApimProperties apimProperties) {
        this.apimWebClientFacade = Objects.requireNonNull(apimWebClientFacade, "apimWebClientFacade must not be null");
        this.apimCertificateService = Objects.requireNonNull(apimCertificateService, "apimCertificateService must not be null");
        this.apimPayloadCryptoService = Objects.requireNonNull(apimPayloadCryptoService, "apimPayloadCryptoService must not be null");
        this.apimProperties = Objects.requireNonNull(apimProperties, "apimProperties must not be null");
        this.legacyTestTransport = null;
        this.requestMapper = new TrpSearchUnregMemberApimMapper();
        this.resultMapper = new TrpSearchUnregMemberApimResultMapper();
    }

    @Override
    @LogExecution(value = "apim.searchUnregisteredMember", logArgs = false)
    public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (legacyTestTransport != null) {
            var request = requestMapper.toApimRequest(command);
            return legacyTestTransport.postSearchUnregisteredMember(API_NAME, request)
                    .map(envelope -> resultMapper.toResult(envelope.errMessage(), envelope.data()));
        }
        if (!apimProperties.getEncryption().isEnabled()) {
            var request = apimPayloadCryptoService.encryptRequest(
                    API_NAME,
                    requestMapper.toApimRequest(command),
                    TrpSearchUnregMemberApimRequest.class,
                    null);
            return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                            API_NAME,
                            body,
                            TrpSearchUnregMemberApimDataItem.class,
                            null))
                    .map(this::toResultPreservingBusinessErrMessage)
                    .onErrorMap(ApimCryptoException.class, this::toApimException);
        }
        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME,
                            requestMapper.toApimRequest(command),
                            TrpSearchUnregMemberApimRequest.class,
                            publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                    API_NAME,
                                    body,
                                    TrpSearchUnregMemberApimDataItem.class,
                                    publicKey))
                            .map(this::toResultPreservingBusinessErrMessage);
                })
                .onErrorMap(ApimCryptoException.class, this::toApimException);
    }

    private RegistrationLookupResult toResultPreservingBusinessErrMessage(
            ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem> envelope) {
        String errMessage = resolveErrMessage(envelope);
        List<TrpSearchUnregMemberApimDataItem> data = resolveData(envelope);
        return resultMapper.toResult(errMessage, data);
    }

    private String resolveErrMessage(ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem> envelope) {
        Object response = invokeAny(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object value = invokeAny(source, "getErrMessage", "errMessage", "getErrmessage", "errmessage");
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<TrpSearchUnregMemberApimDataItem> resolveData(
            ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem> envelope) {
        Object response = invokeAny(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object value = invokeAny(source, "getData", "data");
        if (value instanceof List<?> list) {
            return (List<TrpSearchUnregMemberApimDataItem>) list;
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
                // Try the next known envelope style.
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
