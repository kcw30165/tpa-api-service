package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PersonalInformationUpdateAuthenticatedExecutorBoundaryTest {
    @Test void exactTypedAuthenticatedMutationExchangeIsUsedOnce() {
        AtomicReference<ApimExchangeRequest<?,?>> captured=new AtomicReference<>(); AtomicInteger calls=new AtomicInteger();
        var adapter=new ApimUpdatePersonalInformationAdapter(capturing(captured,calls,success()));
        adapter.updateMemberInfo(command("DB",ActorUserRole.ROLE_USER_EPR)).block();
        var request=captured.get();
        assertThat(calls).hasValue(1);
        assertThat(request.path()).isEqualTo("/ws/NGTPA/v1/TRPUpdMemberInfo");
        assertThat(request.requestType()).isEqualTo(UpdateMemberInfoApimRequest.class);
        assertThat(request.responseDataType()).isEqualTo(UpdateMemberInfoApimDataItem.class);
        assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
        assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);
        assertThat(request.credentialContext().accountEnv()).isEqualTo("DB");
        assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.IDEMPOTENT_MUTATION);
    }
    @Test void blankAccountEnvironmentPreventsExecutorInvocation() {
        AtomicInteger calls=new AtomicInteger();
        assertThatThrownBy(()->new ApimUpdatePersonalInformationAdapter(failing(calls,new AssertionError("must not run"))).updateMemberInfo(command(" ",ActorUserRole.ROLE_USER_MBR)).block()).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void ordinaryExecutorFailureAndUncertainMissingSelectedAccountRemainFeatureOwned() {
        IllegalStateException ordinary=new IllegalStateException("transport uncertain");
        assertThatThrownBy(()->new ApimUpdatePersonalInformationAdapter(failing(new AtomicInteger(),ordinary)).updateMemberInfo(command("DB",ActorUserRole.ROLE_USER_MBR)).block()).isSameAs(ordinary);
        assertThatThrownBy(()->new ApimUpdatePersonalInformationAdapter(fixed(empty())).updateMemberInfo(command("DB",ActorUserRole.ROLE_USER_MBR)).block()).hasMessageContaining("data is missing");
    }
    @Test void adapterHasNoManualOrchestrationRoleSubstitutionOrScopeInference() throws Exception {
        String s=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimUpdatePersonalInformationAdapter.java"));
        assertThat(s).contains("ApimExchangeExecutor","ApimExchangeRequest","postEnvelope","command.userRole()","command.accountEnv()","ACCOUNT_ENV","IDEMPOTENT_MUTATION");
        assertThat(s).doesNotContain("ApimWebClientFacade","ApimCertificateService","ApimPayloadCryptoService","ApimProperties","ROLE_USER_MBR","NON_ACCOUNT","resolveProfile","getPublicKey","encryptRequest","decryptResponseEnvelope");
    }
    private static UpdateMemberInfoCommand command(String env,ActorUserRole role){return new UpdateMemberInfoCommand(env,"POL","CERT","USER",role,true,Map.of("email","a@b.test"));}
    private static ApimResponseEnvelope<UpdateMemberInfoApimDataItem> success(){return ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder().response(ApimResponseBody.<UpdateMemberInfoApimDataItem>builder().errMessage("").data(List.of(UpdateMemberInfoApimDataItem.builder().success(true).policyNo("POL").certNo("CERT").env("DB").refNo("REF").build())).build()).build();}
    private static ApimResponseEnvelope<UpdateMemberInfoApimDataItem> empty(){return ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder().response(ApimResponseBody.<UpdateMemberInfoApimDataItem>builder().errMessage("").data(List.of()).build()).build();}
    private static ApimExchangeExecutor fixed(ApimResponseEnvelope<UpdateMemberInfoApimDataItem> e){return capturing(new AtomicReference<>(),new AtomicInteger(),e);}
    private static ApimExchangeExecutor capturing(AtomicReference<ApimExchangeRequest<?,?>> c,AtomicInteger n,ApimResponseEnvelope<UpdateMemberInfoApimDataItem> e){return new ApimExchangeExecutor(){public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> r){c.set(r);n.incrementAndGet();@SuppressWarnings("unchecked") var x=(ApimResponseEnvelope<D>)(Object)e;return Mono.just(x);}};}
    private static ApimExchangeExecutor failing(AtomicInteger n,Throwable e){return new ApimExchangeExecutor(){public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> r){n.incrementAndGet();return Mono.error(e);}};}
}
