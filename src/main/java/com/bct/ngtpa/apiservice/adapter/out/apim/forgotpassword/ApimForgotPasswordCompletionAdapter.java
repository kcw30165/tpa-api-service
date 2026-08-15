package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCryptoFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpResetPasswordApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpResetPasswordApimResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
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
public class ApimForgotPasswordCompletionAdapter implements ForgotPasswordCompletionPort {
 static final String API_NAME="/ws/NGTPA/v1/TRPResetPassword";
 private final ApimExchangeExecutor executor; private final ApimForgotPasswordCompletionTransport testTransport;
 private final TrpResetPasswordApimMapper mapper=new TrpResetPasswordApimMapper(); private final TrpResetPasswordApimResultMapper resultMapper=new TrpResetPasswordApimResultMapper();
 @Autowired public ApimForgotPasswordCompletionAdapter(ApimExchangeExecutor executor){this.executor=Objects.requireNonNull(executor);this.testTransport=null;}
 public ApimForgotPasswordCompletionAdapter(ApimForgotPasswordCompletionTransport transport){this.executor=null;this.testTransport=Objects.requireNonNull(transport);}
 @Override @LogExecution(value="apim.trpResetPassword",logArgs=false)
 public Mono<ForgotPasswordCompletionResult> complete(ForgotPasswordCompletionCommand command){
  Objects.requireNonNull(command,"command must not be null"); TrpResetPasswordApimRequest request=mapper.toRequest(command);
  if(testTransport!=null)return testTransport.postResetPassword(API_NAME,request).map(e->resultMapper.toResult(e,command.customerId()));
  var exchange=new ApimExchangeRequest<>(API_NAME,request,TrpResetPasswordApimRequest.class,TrpResetPasswordApimDataItem.class,
    ApimDatabaseScope.NON_ACCOUNT,new ApimCredentialResolutionContext(command.actorUserRole(),null),ApimRetryEligibility.IDEMPOTENT_MUTATION);
  return executor.postEnvelope(exchange).map(this::toEnvelope).map(e->resultMapper.toResult(e,command.customerId()))
    .onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure,e->Mono.just(ForgotPasswordCompletionResult.unknownOutcome()))
    .onErrorMap(this::safeFailure);
 }
 private TrpResetPasswordApimEnvelope toEnvelope(ApimResponseEnvelope<TrpResetPasswordApimDataItem> envelope){
  if(envelope==null||envelope.getResponse()==null)throw new ApimException(HttpStatus.BAD_GATEWAY,ErrorCodes.APIM_RESPONSE_INVALID,"TRPResetPassword response envelope is missing response");
  return new TrpResetPasswordApimEnvelope(envelope.getResponse().getErrMessage(),envelope.getResponse().getData()==null?List.of():envelope.getResponse().getData());
 }
 private Throwable safeFailure(Throwable t){if(t instanceof ApimException)return t;return new ApimException(HttpStatus.INTERNAL_SERVER_ERROR,ErrorCodes.SYSTEM_UNEXPECTED,"TRPResetPassword transport failed",t);}
}
