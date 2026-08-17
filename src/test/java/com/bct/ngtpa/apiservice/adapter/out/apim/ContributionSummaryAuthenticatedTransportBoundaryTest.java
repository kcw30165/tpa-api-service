package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ContributionSummaryAuthenticatedTransportBoundaryTest {
 @Test void exactTypedAuthenticatedExchangeIsUsedOnce(){
  AtomicReference<ApimExchangeRequest<?,?>> seen=new AtomicReference<>(); AtomicInteger calls=new AtomicInteger();
  var adapter=new ApimContributionSummaryAdapter(executor(seen,calls,envelope()));
  adapter.fetchContributionSummary(command("JP",ActorUserRole.ROLE_USER_EPR)).block();
  var r=seen.get(); assertThat(calls).hasValue(1); assertThat(r.path()).isEqualTo("/ws/NGTPA/v1/TRPGetContSumy");
  assertThat(r.requestType()).isEqualTo(GetContributionSummaryApimRequest.class); assertThat(r.responseDataType()).isEqualTo(GetContributionSummaryDataItem.class);
  assertThat(r.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV); assertThat(r.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);
  assertThat(r.credentialContext().accountEnv()).isEqualTo("JP"); assertThat(r.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
  var body=(GetContributionSummaryApimRequest)r.request(); assertThat(body.getPolicyNo()).isEqualTo("POL"); assertThat(body.getCertNo()).isEqualTo("27");
  assertThat(body.getUserId()).isEqualTo("USER"); assertThat(body.getCoverFrom()).isEqualTo("20/03/2026"); assertThat(body.getCoverTo()).isEqualTo("29/06/2026");
 }
 @Test void blankEnvironmentPreventsInvocation(){ AtomicInteger calls=new AtomicInteger(); assertThatThrownBy(()->new ApimContributionSummaryAdapter(executor(new AtomicReference<>(),calls,envelope())).fetchContributionSummary(command(" ",ActorUserRole.ROLE_USER_MBR)).block()).isInstanceOf(IllegalArgumentException.class); assertThat(calls).hasValue(0); }
 @Test void ordinaryExecutorFailureIsUnchanged(){ var failure=new IllegalStateException("transport failed"); assertThatThrownBy(()->new ApimContributionSummaryAdapter(failing(failure)).fetchContributionSummary(command("JP",ActorUserRole.ROLE_USER_MBR)).block()).isSameAs(failure); }
 @Test void focusedCommandAndAdapterExcludeFallbackAndManualTransport() throws Exception {
  var components=FetchContributionSummaryCommand.class.getRecordComponents(); assertThat(components).extracting(java.lang.reflect.RecordComponent::getName).containsExactly("policyNo","certNo","userId","coverFrom","coverTo","accountEnv","actorUserRole");
  String s=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimContributionSummaryAdapter.java"));
  assertThat(s).contains("ApimExchangeExecutor","ApimExchangeRequest","postEnvelope","ACCOUNT_ENV","command.actorUserRole()","command.accountEnv()","SAFE_BUSINESS_CALL");
  assertThat(s).doesNotContain("NON_ACCOUNT","ROLE_USER_MBR","ApimWebClientFacade","ApimCertificateService","ApimPayloadCryptoService","ApimProperties","resolveProfile","encryptRequest","decryptResponseEnvelope");
 }
 private static FetchContributionSummaryCommand command(String env,ActorUserRole role){return new FetchContributionSummaryCommand("POL","27","USER","20/03/2026","29/06/2026",env,role);}
 private static ApimResponseEnvelope<GetContributionSummaryDataItem> envelope(){return ApimResponseEnvelope.<GetContributionSummaryDataItem>builder().response(ApimResponseBody.<GetContributionSummaryDataItem>builder().errMessage("").data(List.of(GetContributionSummaryDataItem.builder().currency("HKD").dispSrc(List.of()).contDtl(List.of()).build())).build()).build();}
 private static ApimExchangeExecutor executor(AtomicReference<ApimExchangeRequest<?,?>> seen,AtomicInteger calls,ApimResponseEnvelope<GetContributionSummaryDataItem> envelope){return new ApimExchangeExecutor(){public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> r){seen.set(r);calls.incrementAndGet();@SuppressWarnings("unchecked") var x=(ApimResponseEnvelope<D>)(Object)envelope;return Mono.just(x);}};}
 private static ApimExchangeExecutor failing(Throwable failure){return new ApimExchangeExecutor(){public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> r){return Mono.error(failure);}};}
}
