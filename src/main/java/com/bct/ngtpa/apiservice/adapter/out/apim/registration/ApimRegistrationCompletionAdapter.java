package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.*;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCompletionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.*;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.security.PublicKey;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimRegistrationCompletionAdapter implements RegistrationCompletionPort {
    static final String API_NAME = "/ws/NGTPA/v1/TRPRegMember";
    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties properties;
    private final ApimRegistrationCompletionTransport testTransport;
    private final TrpRegMemberApimMapper mapper = new TrpRegMemberApimMapper();
    private final TrpRegMemberApimResultMapper resultMapper = new TrpRegMemberApimResultMapper();

    @Autowired
    public ApimRegistrationCompletionAdapter(ApimWebClientFacade facade, ApimCertificateService certificates,
            ApimPayloadCryptoService crypto, ApimProperties properties) {
        this.facade = Objects.requireNonNull(facade);
        this.certificates = Objects.requireNonNull(certificates);
        this.crypto = Objects.requireNonNull(crypto);
        this.properties = Objects.requireNonNull(properties);
        this.testTransport = null;
    }

    public ApimRegistrationCompletionAdapter(ApimRegistrationCompletionTransport transport) {
        this.facade = null;
        this.certificates = null;
        this.crypto = null;
        this.properties = null;
        this.testTransport = Objects.requireNonNull(transport);
    }

    @Override
    @LogExecution(value = "apim.trpRegMember", logArgs = false)
    public Mono<RegistrationCompletionResult> complete(RegistrationCompletionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TrpRegMemberApimRequest request = mapper.toRequest(command);
        if (testTransport != null)
            return testTransport.postRegMember(API_NAME, request).map(resultMapper::toResult);
        if (!properties.getEncryption().isEnabled())
            return post(request, null, command.accountEnv());
        var ctx = new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext(com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, command.accountEnv());
        return facade.resolveProfile(com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.ACCOUNT_ENV, ctx)
                .flatMap(profile -> certificates.getPublicKey(profile)
                        .flatMap(key -> {
                            var encryptedRequest = crypto.encryptRequest(API_NAME, request, TrpRegMemberApimRequest.class, key);
                            return facade.post(API_NAME, encryptedRequest, profile, com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility.IDEMPOTENT_MUTATION)
                                    .map(body -> crypto.decryptResponseEnvelope(API_NAME, body, TrpRegMemberApimDataItem.class, key))
                                    .map(this::toEnvelope)
                                    .map(resultMapper::toResult)
                                    .onErrorMap(ApimCryptoException.class, this::cryptoFailure);
                        }));
    }

    private Mono<RegistrationCompletionResult> post(
            TrpRegMemberApimRequest request,
            PublicKey publicKey,
            String accountEnv) {
        var encryptedRequest = crypto.encryptRequest(
                API_NAME,
                request,
                TrpRegMemberApimRequest.class,
                publicKey);
        return facade.post(API_NAME, encryptedRequest, com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.ACCOUNT_ENV, new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext(com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, accountEnv), com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility.IDEMPOTENT_MUTATION)
                .map(body -> crypto.decryptResponseEnvelope(
                        API_NAME,
                        body,
                        TrpRegMemberApimDataItem.class,
                        publicKey))
                .map(this::toEnvelope)
                .map(resultMapper::toResult)
                .onErrorMap(ApimCryptoException.class, this::cryptoFailure);
    }

    private TrpRegMemberApimEnvelope toEnvelope(ApimResponseEnvelope<TrpRegMemberApimDataItem> envelope) {
        Object response = invoke(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object err = invoke(source, "getErrMessage", "errMessage", "getErrmessage", "errmessage");
        Object data = invoke(source, "getData", "data");
        @SuppressWarnings("unchecked")
        List<TrpRegMemberApimDataItem> items = data instanceof List<?> l ? (List<TrpRegMemberApimDataItem>) l
                : List.of();
        return new TrpRegMemberApimEnvelope(err == null ? "" : String.valueOf(err), items);
    }

    private Object invoke(Object target, String... names) {
        if (target == null)
            return null;
        for (String n : names)
            try {
                return target.getClass().getMethod(n).invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        return null;
    }

    private ApimException cryptoFailure(ApimCryptoException e) {
        return new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, e.getMessage(), e);
    }
}
