package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.*;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
class TrpSearchMemberAdapterTest {
 @Test void capturesTypedNonAccountSafeBusinessExchange(){
  ApimExchangeExecutor x=mock(ApimExchangeExecutor.class);
  when(x.postEnvelope(any())).thenAnswer(i->Mono.just(new ApimResponseEnvelope<>(new ApimResponseBody<>("",List.of(item())))));
  var a=new TrpSearchMemberAdapter(x);
  StepVerifier.create(a.search(command())).assertNext(r->assertThat(r.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED)).verifyComplete();
  @SuppressWarnings("rawtypes") ArgumentCaptor<ApimExchangeRequest> c=ArgumentCaptor.forClass(ApimExchangeRequest.class); verify(x).postEnvelope(c.capture());
  var q=c.getValue(); assertThat(q.path()).isEqualTo(TrpSearchMemberAdapter.API_NAME); assertThat(q.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT); assertThat(q.credentialContext().accountEnv()).isNull(); assertThat(q.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR); assertThat(q.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
 }
 private static UsernameRecoveryLookupCommand command(){return new UsernameRecoveryLookupCommand(MemberIdentityType.ID,"A1234567","15/07/1969","a@b.com","",ActorUserRole.ROLE_USER_MBR);}
 private static TrpSearchMemberApimDataItem item(){return new TrpSearchMemberApimDataItem("DB", "DBA", "MPF", "0001", "0002", "A", "", true, "user-1");}
}
