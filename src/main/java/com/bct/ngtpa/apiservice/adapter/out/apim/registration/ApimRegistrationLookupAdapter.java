package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.LinkedHashMap;
import java.util.Map;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
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
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
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
        private final TrpSearchUnregMemberApimMapper requestMapper;
        private final TrpSearchUnregMemberApimResultMapper resultMapper;
        private final FailureEventPort failureEventPort;

        @Autowired
        public ApimRegistrationLookupAdapter(
                        ApimWebClientFacade apimWebClientFacade,
                        ApimCertificateService apimCertificateService,
                        ApimPayloadCryptoService apimPayloadCryptoService,
                        ApimProperties apimProperties,
                        FailureEventPort failureEventPort) {
                this.apimWebClientFacade = Objects.requireNonNull(apimWebClientFacade,
                                "apimWebClientFacade must not be null");
                this.apimCertificateService = Objects.requireNonNull(apimCertificateService,
                                "apimCertificateService must not be null");
                this.apimPayloadCryptoService = Objects.requireNonNull(apimPayloadCryptoService,
                                "apimPayloadCryptoService must not be null");
                this.apimProperties = Objects.requireNonNull(apimProperties, "apimProperties must not be null");
                this.requestMapper = new TrpSearchUnregMemberApimMapper();
                this.resultMapper = new TrpSearchUnregMemberApimResultMapper();
                this.failureEventPort = Objects.requireNonNull(
                                failureEventPort, "failureEventPort must not be null");
        }

        @Override
        @LogExecution(value = "apim.searchUnregisteredMember", logArgs = false)
        public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) {
                Objects.requireNonNull(command, "command must not be null");
                if (!apimProperties.getEncryption().isEnabled()) {
                        var request = apimPayloadCryptoService.encryptRequest(
                                        API_NAME,
                                        requestMapper.toApimRequest(command),
                                        TrpSearchUnregMemberApimRequest.class,
                                        null);
                        return withFailureLogging(
                                        apimWebClientFacade.post(API_NAME, request, com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT, new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext(com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER, null), com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility.SAFE_BUSINESS_CALL)
                                                        .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                                                        API_NAME,
                                                                        body,
                                                                        TrpSearchUnregMemberApimDataItem.class,
                                                                        null))
                                                        .map(envelope -> toResult(envelope, command))
                                                        .onErrorMap(ApimCryptoException.class, this::toApimException),
                                        command);
                }
                return withFailureLogging(
                                apimCertificateService.getBctPublicKey()
                                                .flatMap(publicKey -> {
                                                        var request = apimPayloadCryptoService.encryptRequest(
                                                                        API_NAME,
                                                                        requestMapper.toApimRequest(command),
                                                                        TrpSearchUnregMemberApimRequest.class,
                                                                        publicKey);
                                                        return apimWebClientFacade.post(API_NAME, request, com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT, new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext(com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER, null), com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility.SAFE_BUSINESS_CALL)
                                                                        .map(body -> apimPayloadCryptoService
                                                                                        .decryptResponseEnvelope(
                                                                                                        API_NAME,
                                                                                                        body,
                                                                                                        TrpSearchUnregMemberApimDataItem.class,
                                                                                                        publicKey))
                                                                        .map(envelope -> toResult(envelope, command));
                                                })
                                                .onErrorMap(ApimCryptoException.class, this::toApimException),
                                command);
        }

        private RegistrationLookupResult toResult(
                        ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem> envelope,
                        RegistrationLookupCommand command) {
                if (envelope == null || envelope.getResponse() == null) {
                        throw new RegistrationLookupDownstreamException(
                                        RegistrationLookupDownstreamException.Reason.EMPTY_DATA,
                                        null);
                }
                var response = envelope.getResponse();
                List<TrpSearchUnregMemberApimDataItem> data = response.getData() == null
                                ? List.of()
                                : List.copyOf(response.getData());
                return resultMapper.toResult(response.getErrMessage(), data, command);
        }

        private Mono<RegistrationLookupResult> withFailureLogging(
                        Mono<RegistrationLookupResult> pipeline,
                        RegistrationLookupCommand command) {
                return pipeline.doOnEach(signal -> {
                        if (signal.isOnNext()
                                        && signal.get() != null
                                        && signal.get().status() == RegistrationLookupStatus.NOT_ELIGIBLE) {
                                String requestId = signal.getContextView().getOrDefault(
                                                RequestCorrelation.REQUEST_ID_CONTEXT_KEY,
                                                null);
                                recordDownstreamRejection(command, requestId);
                                return;
                        }
                        if (!signal.isOnError()
                                        || !(signal.getThrowable() instanceof RegistrationLookupDownstreamException exception)) {
                                return;
                        }
                        String requestId = signal.getContextView().getOrDefault(
                                        RequestCorrelation.REQUEST_ID_CONTEXT_KEY,
                                        null);
                        var dimensions = new LinkedHashMap<String, Object>();
                        dimensions.put("searchBy", command.identityType().name());
                        dimensions.put("identityValue", RegistrationLookupLogMasker.identity(command.identityValue()));
                        dimensions.put("dateOfBirth", RegistrationLookupLogMasker.dateOfBirth(command.dateOfBirth()));
                        dimensions.put("contactType", command.contactType().name());
                        dimensions.put("contactValue", RegistrationLookupLogMasker.contact(
                                        command.contactType(), command.contactValue()));
                        dimensions.put("reason", exception.reason().name());
                        failureEventPort.record(new FailureEvent(
                                        "registration.lookup.downstream_contract_failure",
                                        requestId,
                                        "REGISTRATION",
                                        "TRPSearchUnregMember",
                                        FailureOrigin.DOWNSTREAM_CONTRACT,
                                        exception.downstreamErrorCode(),
                                        SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(),
                                        503,
                                        true,
                                        Map.copyOf(dimensions)));
                });
        }

        private void recordDownstreamRejection(
                        RegistrationLookupCommand command,
                        String requestId) {
                var dimensions = new LinkedHashMap<String, Object>();
                dimensions.put("searchBy", command.identityType().name());
                dimensions.put("identityValue", RegistrationLookupLogMasker.identity(command.identityValue()));
                dimensions.put("dateOfBirth", RegistrationLookupLogMasker.dateOfBirth(command.dateOfBirth()));
                dimensions.put("contactType", command.contactType().name());
                dimensions.put("contactValue", RegistrationLookupLogMasker.contact(
                                command.contactType(), command.contactValue()));
                dimensions.put("outcome", RegistrationLookupStatus.NOT_ELIGIBLE.name());

                failureEventPort.record(new FailureEvent(
                                "event.registration.lookup.downstream_rejection",
                                requestId,
                                "REGISTRATION",
                                "TRPSearchUnregMember",
                                FailureOrigin.DOWNSTREAM_CONTRACT,
                                "err.tpa.member.not.found",
                                RegistrationErrorKey.START_VERIFICATION_FAILED.value(),
                                422,
                                false,
                                Map.copyOf(dimensions)));
        }

        private ApimException toApimException(ApimCryptoException exception) {
                return new ApimException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                ErrorCodes.SYSTEM_UNEXPECTED,
                                exception.getMessage(),
                                exception);
        }
}
