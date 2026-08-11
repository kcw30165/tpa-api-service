package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCryptoFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpResetPasswordApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpResetPasswordApimResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimForgotPasswordCompletionAdapter implements ForgotPasswordCompletionPort {
    static final String API_NAME = "/ws/NGTPA/v1/TRPResetPassword";

    private final ApimWebClientFacade facade;
    private final ApimCertificateService certificates;
    private final ApimPayloadCryptoService crypto;
    private final ApimProperties properties;
    private final ApimForgotPasswordCompletionTransport testTransport;
    private final TrpResetPasswordApimMapper mapper = new TrpResetPasswordApimMapper();
    private final TrpResetPasswordApimResultMapper resultMapper = new TrpResetPasswordApimResultMapper();

    @Autowired
    public ApimForgotPasswordCompletionAdapter(
            ApimWebClientFacade facade,
            ApimCertificateService certificates,
            ApimPayloadCryptoService crypto,
            ApimProperties properties) {
        this.facade = Objects.requireNonNull(facade);
        this.certificates = Objects.requireNonNull(certificates);
        this.crypto = Objects.requireNonNull(crypto);
        this.properties = Objects.requireNonNull(properties);
        this.testTransport = null;
    }

    public ApimForgotPasswordCompletionAdapter(ApimForgotPasswordCompletionTransport transport) {
        this.facade = null;
        this.certificates = null;
        this.crypto = null;
        this.properties = null;
        this.testTransport = Objects.requireNonNull(transport);
    }

    @Override
    @LogExecution(value = "apim.trpResetPassword", logArgs = false)
    public Mono<ForgotPasswordCompletionResult> complete(
            ForgotPasswordCompletionCommand command) {

        Objects.requireNonNull(command, "command must not be null");

        TrpResetPasswordApimRequest request = mapper.toRequest(command);

        if (testTransport != null) {
            return testTransport.postResetPassword(API_NAME, request)
                    .map(envelope -> resultMapper.toResult(
                            envelope,
                            command.customerId()));
        }

        if (!properties.getEncryption().isEnabled()) {
            return post(
                    command.customerId(),
                    command.accountEnv(),
                    request,
                    null);
        }

        return certificates.getBctPublicKey()
                .flatMap(key -> post(
                        command.customerId(),
                        command.accountEnv(),
                        request,
                        key));
    }

    private Mono<ForgotPasswordCompletionResult> post(
            String expectedCustomerId,
            String accountEnv,
            TrpResetPasswordApimRequest request,
            PublicKey publicKey) {

        var encryptedRequest = crypto.encryptRequest(
                API_NAME,
                request,
                TrpResetPasswordApimRequest.class,
                publicKey);

        return facade.post(
                API_NAME,
                encryptedRequest,
                ApimDatabaseScope.ACCOUNT_ENV,
                new ApimCredentialResolutionContext(
                        ActorUserRole.ROLE_USER,
                        accountEnv),
                ApimRetryEligibility.IDEMPOTENT_MUTATION)
                .map(body -> crypto.decryptResponseEnvelope(
                        API_NAME,
                        body,
                        TrpResetPasswordApimDataItem.class,
                        publicKey))
                .map(this::toEnvelope)
                .map(envelope -> resultMapper.toResult(
                        envelope,
                        expectedCustomerId))
                .onErrorResume(
                        ApimCryptoFailureClassifier::isCryptoFailure,
                        exception -> Mono.just(
                                ForgotPasswordCompletionResult.unknownOutcome()))
                .onErrorMap(this::safeFailure);
    }

    private TrpResetPasswordApimEnvelope toEnvelope(ApimResponseEnvelope<TrpResetPasswordApimDataItem> envelope) {
        Object response = invoke(envelope, "getResponse", "response");
        Object source = response == null ? envelope : response;
        Object err = invoke(source, "getErrMessage", "errMessage", "getErrmessage", "errmessage");
        Object data = invoke(source, "getData", "data");
        @SuppressWarnings("unchecked")
        List<TrpResetPasswordApimDataItem> items = data instanceof List<?> list
                ? (List<TrpResetPasswordApimDataItem>) list
                : List.of();
        return new TrpResetPasswordApimEnvelope(err == null ? null : String.valueOf(err), items);
    }

    private Object invoke(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private Throwable safeFailure(Throwable throwable) {
        if (throwable instanceof ApimException) {
            return throwable;
        }
        return new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED,
                "TRPResetPassword transport failed", throwable);
    }
}