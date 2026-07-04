package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimResultMapper;
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
    private static final String API_NAME = "/ws/NGTPA/v1/TRPSearchUnregisteredMember";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;
    private final ApimRegistrationLookupTransport legacyTestTransport;
    private final TrpSearchUnregisteredMemberApimMapper requestMapper;
    private final TrpSearchUnregisteredMemberApimResultMapper resultMapper;

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
        this.requestMapper = new TrpSearchUnregisteredMemberApimMapper();
        this.resultMapper = new TrpSearchUnregisteredMemberApimResultMapper();
    }

    /**
     * Backwards-compatible constructor for already-generated unit tests only.
     * Spring uses the @Autowired constructor above, so no ApimRegistrationLookupTransport bean is required.
     */
    // @Deprecated(forRemoval = true)
    // ApimRegistrationLookupAdapter(
    //         ApimRegistrationLookupTransport legacyTestTransport,
    //         TrpSearchUnregisteredMemberApimMapper requestMapper,
    //         TrpSearchUnregisteredMemberApimResultMapper resultMapper) {
    //     this.apimWebClientFacade = null;
    //     this.apimCertificateService = null;
    //     this.apimPayloadCryptoService = null;
    //     this.apimProperties = null;
    //     this.legacyTestTransport = Objects.requireNonNull(legacyTestTransport, "legacyTestTransport must not be null");
    //     this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper must not be null");
    //     this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper must not be null");
    // }

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
                    TrpSearchUnregisteredMemberApimRequest.class,
                    null);
            return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                            API_NAME,
                            body,
                            TrpSearchUnregisteredMemberApimDataItem.class,
                            null))
                    .map(this::toResultPreservingBusinessErrMessage)
                    .onErrorMap(ApimCryptoException.class, this::toApimException);
        }
        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME,
                            requestMapper.toApimRequest(command),
                            TrpSearchUnregisteredMemberApimRequest.class,
                            publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                    API_NAME,
                                    body,
                                    TrpSearchUnregisteredMemberApimDataItem.class,
                                    publicKey))
                            .map(this::toResultPreservingBusinessErrMessage);
                })
                .onErrorMap(ApimCryptoException.class, this::toApimException);
    }

    private RegistrationLookupResult toResultPreservingBusinessErrMessage(
            ApimResponseEnvelope<TrpSearchUnregisteredMemberApimDataItem> envelope) {
        String errMessage = resolveErrMessage(envelope);
        List<TrpSearchUnregisteredMemberApimDataItem> data = resolveData(envelope);
        return resultMapper.toResult(errMessage, data);
    }

    private String resolveErrMessage(ApimResponseEnvelope<TrpSearchUnregisteredMemberApimDataItem> envelope) {
        Object response = invokeAny(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object value = invokeAny(source, "getErrMessage", "errMessage", "getErrmessage", "errmessage");
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<TrpSearchUnregisteredMemberApimDataItem> resolveData(
            ApimResponseEnvelope<TrpSearchUnregisteredMemberApimDataItem> envelope) {
        Object response = invokeAny(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object value = invokeAny(source, "getData", "data");
        if (value instanceof List<?> list) {
            return (List<TrpSearchUnregisteredMemberApimDataItem>) list;
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
