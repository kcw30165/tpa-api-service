package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimRegistrationLookupAdapter implements RegistrationLookupPort {
    private static final String API_NAME = "/ws/NGTPA/v1/TRPSearchUnregMember";
    private final ApimExchangeExecutor executor;
    private final TrpSearchUnregMemberApimMapper requestMapper;
    private final TrpSearchUnregMemberApimResultMapper resultMapper;
    private final FailureEventPort failureEventPort;

    public ApimRegistrationLookupAdapter(ApimExchangeExecutor executor, FailureEventPort failureEventPort) {
        this(executor, new TrpSearchUnregMemberApimMapper(), new TrpSearchUnregMemberApimResultMapper(), failureEventPort);
    }
    ApimRegistrationLookupAdapter(ApimExchangeExecutor executor, TrpSearchUnregMemberApimMapper requestMapper,
            TrpSearchUnregMemberApimResultMapper resultMapper, FailureEventPort failureEventPort) {
        this.executor=Objects.requireNonNull(executor,"executor must not be null");
        this.requestMapper=Objects.requireNonNull(requestMapper,"requestMapper must not be null");
        this.resultMapper=Objects.requireNonNull(resultMapper,"resultMapper must not be null");
        this.failureEventPort=Objects.requireNonNull(failureEventPort,"failureEventPort must not be null");
    }

    @Override @LogExecution(value="apim.searchUnregisteredMember",logArgs=false)
    public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) {
        Objects.requireNonNull(command,"command must not be null");
        TrpSearchUnregMemberApimRequest body=requestMapper.toApimRequest(command);
        var request=new ApimExchangeRequest<>(API_NAME,body,TrpSearchUnregMemberApimRequest.class,
                TrpSearchUnregMemberApimDataItem.class,ApimDatabaseScope.NON_ACCOUNT,
                new ApimCredentialResolutionContext(command.actorUserRole(),null),
                ApimRetryEligibility.SAFE_BUSINESS_CALL);
        return withFailureLogging(executor.postEnvelope(request).map(envelope->toResult(envelope,command)),command);
    }

    private RegistrationLookupResult toResult(ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem> envelope,
            RegistrationLookupCommand command) {
        if(envelope==null||envelope.getResponse()==null) throw new RegistrationLookupDownstreamException(
                RegistrationLookupDownstreamException.Reason.EMPTY_DATA,null);
        var response=envelope.getResponse();
        List<TrpSearchUnregMemberApimDataItem> data=response.getData()==null?List.of():List.copyOf(response.getData());
        return resultMapper.toResult(response.getErrMessage(),data,command);
    }

    private Mono<RegistrationLookupResult> withFailureLogging(Mono<RegistrationLookupResult> pipeline,
            RegistrationLookupCommand command) {
        return pipeline.doOnEach(signal->{
            if(signal.isOnNext()&&signal.get()!=null&&signal.get().status()==RegistrationLookupStatus.NOT_ELIGIBLE){
                recordDownstreamRejection(command,signal.getContextView().getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY,null));
                return;
            }
            if(!signal.isOnError()||!(signal.getThrowable() instanceof RegistrationLookupDownstreamException exception)) return;
            String requestId=signal.getContextView().getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY,null);
            var dimensions=safeDimensions(command);
            dimensions.put("reason",exception.reason().name());
            failureEventPort.record(new FailureEvent("registration.lookup.downstream_contract_failure",requestId,
                    "REGISTRATION","TRPSearchUnregMember",FailureOrigin.DOWNSTREAM_CONTRACT,
                    exception.downstreamErrorCode(),SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(),503,true,
                    Map.copyOf(dimensions)));
        });
    }
    private LinkedHashMap<String,Object> safeDimensions(RegistrationLookupCommand command){
        var d=new LinkedHashMap<String,Object>();
        d.put("searchBy",command.identityType().name());
        d.put("identityValue",RegistrationLookupLogMasker.identity(command.identityValue()));
        d.put("dateOfBirth",RegistrationLookupLogMasker.dateOfBirth(command.dateOfBirth()));
        d.put("contactType",command.contactType().name());
        d.put("contactValue",RegistrationLookupLogMasker.contact(command.contactType(),command.contactValue()));
        return d;
    }
    private void recordDownstreamRejection(RegistrationLookupCommand command,String requestId){
        var dimensions=safeDimensions(command); dimensions.put("outcome",RegistrationLookupStatus.NOT_ELIGIBLE.name());
        failureEventPort.record(new FailureEvent("event.registration.lookup.downstream_rejection",requestId,
                "REGISTRATION","TRPSearchUnregMember",FailureOrigin.DOWNSTREAM_CONTRACT,"err.tpa.member.not.found",
                RegistrationErrorKey.START_VERIFICATION_FAILED.value(),422,false,Map.copyOf(dimensions)));
    }
}
