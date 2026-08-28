package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;
import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
class ApimAccountBalanceSourceAdapterTest {
 @Test void routesMapsAndReturnsRawData() {
  AtomicReference<ApimExchangeRequest<?,?>> seen=new AtomicReference<>();
  var item=new TrpGetMemberAccBalApimDataItem(new AccountBalanceDisplayOptions(true,false,true),new AccountBalanceCurrency(List.of("HKD"),"HKD"),List.of(),List.of(),List.of());
  ApimExchangeExecutor executor=new ApimExchangeExecutor(){ public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> r){seen.set(r); return Mono.just(ApimResponseEnvelope.<D>builder().response(ApimResponseBody.<D>builder().errMessage("").data((List<D>)List.of(item)).build()).build());}};
  var adapter=new ApimAccountBalanceSourceAdapter(executor,properties());
  var command=new FetchAccountBalanceCommand("en",null,"JP","00000008802","0230","T","S","db230",ActorUserRole.ROLE_USER_MBR,LocalDate.of(2026,8,31));
  StepVerifier.create(adapter.fetchRawAccountBalance(command)).assertNext(raw->assertThat(raw.currency().selected()).isEqualTo("HKD")).verifyComplete();
  var exchange=seen.get(); assertThat(exchange.path()).isEqualTo("/configured/member-balance"); assertThat(exchange.databaseScope().name()).isEqualTo("ACCOUNT_ENV"); assertThat(exchange.retryEligibility().name()).isEqualTo("SAFE_BUSINESS_CALL");
  var request=(TrpGetMemberAccBalApimRequest)exchange.request(); assertThat(request.policyNo()).isEqualTo("00000008802"); assertThat(request.certNo()).isEqualTo(230); assertThat(request.referenceDate()).isEqualTo("31/08/2026"); assertThat(request.currency()).isEmpty();
 }
 @Test void classifiesApprovedAndTechnicalErrorsWithoutRawCause() {
  assertReason("err.tpa.account.invalid",AccountBalanceRetrievalException.Reason.ACCOUNT_NOT_FOUND);
  assertReason("err.tpa.member.not.found",AccountBalanceRetrievalException.Reason.ACCOUNT_MISMATCH);
  assertReason("err.tpa.policy.no.required",AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE);
 }
 private void assertReason(String code,AccountBalanceRetrievalException.Reason reason){ ApimExchangeExecutor x=new ApimExchangeExecutor(){public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> r){return Mono.just(ApimResponseEnvelope.<D>builder().response(ApimResponseBody.<D>builder().errMessage(code).build()).build());}}; StepVerifier.create(new ApimAccountBalanceSourceAdapter(x,properties()).fetchRawAccountBalance(command())).expectErrorSatisfies(e->{assertThat(e).isInstanceOf(AccountBalanceRetrievalException.class); assertThat(((AccountBalanceRetrievalException)e).reason()).isEqualTo(reason); assertThat(e.getCause()).isNull(); assertThat(e.toString()).doesNotContain("err.tpa");}).verify(); }
 private static AccountBalanceApimProperties properties(){var p=new AccountBalanceApimProperties();p.setPath(" /configured/member-balance ");p.validate();return p;}
 private static FetchAccountBalanceCommand command(){return new FetchAccountBalanceCommand("en",null,"JP","P","1","T","S","U",ActorUserRole.ROLE_USER_MBR,LocalDate.of(2026,8,31));}
}
