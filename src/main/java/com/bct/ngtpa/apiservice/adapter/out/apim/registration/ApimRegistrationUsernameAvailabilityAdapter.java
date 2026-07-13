package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimResultMapper;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
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
public class ApimRegistrationUsernameAvailabilityAdapter implements RegistrationUsernameAvailabilityPort {
    private static final String API_NAME = "/ws/NGTPA/v1/TRPChkCustId";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;
    private final ApimRegistrationUsernameAvailabilityTransport testTransport;
    private final TrpChkCustIdApimMapper requestMapper;
    private final TrpChkCustIdApimResultMapper resultMapper;

    @Autowired
    public ApimRegistrationUsernameAvailabilityAdapter(
            ApimWebClientFacade apimWebClientFacade,
            ApimCertificateService apimCertificateService,
            ApimPayloadCryptoService apimPayloadCryptoService,
            ApimProperties apimProperties) {
        this.apimWebClientFacade = Objects.requireNonNull(apimWebClientFacade, "apimWebClientFacade must not be null");
        this.apimCertificateService = Objects.requireNonNull(apimCertificateService, "apimCertificateService must not be null");
        this.apimPayloadCryptoService = Objects.requireNonNull(apimPayloadCryptoService, "apimPayloadCryptoService must not be null");
        this.apimProperties = Objects.requireNonNull(apimProperties, "apimProperties must not be null");
        this.testTransport = null;
        this.requestMapper = new TrpChkCustIdApimMapper();
        this.resultMapper = new TrpChkCustIdApimResultMapper();
    }

    public ApimRegistrationUsernameAvailabilityAdapter(
            ApimRegistrationUsernameAvailabilityTransport testTransport) {
        this.apimWebClientFacade = null;
        this.apimCertificateService = null;
        this.apimPayloadCryptoService = null;
        this.apimProperties = null;
        this.testTransport = Objects.requireNonNull(testTransport, "testTransport must not be null");
        this.requestMapper = new TrpChkCustIdApimMapper();
        this.resultMapper = new TrpChkCustIdApimResultMapper();
    }

    @Override
    @LogExecution(value = "apim.trpChkCustId", logArgs = false)
    public Mono<RegistrationUsernameAvailabilityResult> checkAvailability(
            RegistrationUsernameAvailabilityCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TrpChkCustIdApimRequest request = requestMapper.toApimRequest(command);
        if (testTransport != null) {
            return testTransport.postChkCustId(API_NAME, request)
                    .map(resultMapper::toResult);
        }
        if (!apimProperties.getEncryption().isEnabled()) {
            var encryptedRequest = apimPayloadCryptoService.encryptRequest(
                    API_NAME,
                    request,
                    TrpChkCustIdApimRequest.class,
                    null);
            return apimWebClientFacade.post(API_NAME, encryptedRequest)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                            API_NAME,
                            body,
                            TrpChkCustIdApimDataItem.class,
                            null))
                    .map(this::toEnvelopePreservingBusinessErrMessage)
                    .map(resultMapper::toResult)
                    .onErrorMap(ApimCryptoException.class, this::toApimException);
        }
        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var encryptedRequest = apimPayloadCryptoService.encryptRequest(
                            API_NAME,
                            request,
                            TrpChkCustIdApimRequest.class,
                            publicKey);
                    return apimWebClientFacade.post(API_NAME, encryptedRequest)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                    API_NAME,
                                    body,
                                    TrpChkCustIdApimDataItem.class,
                                    publicKey))
                            .map(this::toEnvelopePreservingBusinessErrMessage)
                            .map(resultMapper::toResult);
                })
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
    private List<TrpChkCustIdApimDataItem> resolveData(
            ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
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
