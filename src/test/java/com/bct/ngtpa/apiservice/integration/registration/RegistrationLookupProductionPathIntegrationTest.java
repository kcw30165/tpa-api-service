package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.*;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import java.util.List;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RegistrationLookupProductionPathIntegrationTest {
  @Test
  void validLookupInvokesTypedExecutorExactlyOnceAndPreservesBody() {
    AtomicReference<ApimExchangeRequest<?, ?>> seen = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    var executor =
        capturing(seen, calls, envelope("", List.of(item("member@example.com", "", "OI", "JPM"))));
    var adapter = new ApimRegistrationLookupAdapter(executor, mock(FailureEventPort.class));
    var result = adapter.searchMember(command(MemberIdentityType.STAFFNO)).block();
    assertThat(result.status()).isEqualTo(RegistrationLookupStatus.ELIGIBLE);
    assertThat(calls).hasValue(1);
    var exchange = seen.get();
    assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPSearchMember");
    assertThat(exchange.databaseScope().name()).isEqualTo("NON_ACCOUNT");
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(exchange.credentialContext().accountEnv()).isNull();
    assertThat(exchange.retryEligibility().name()).isEqualTo("SAFE_BUSINESS_CALL");
    assertThat(exchange.request()).isInstanceOf(TrpSearchMemberApimRequest.class);
    var body = (TrpSearchMemberApimRequest) exchange.request();
    assertThat(body.searchBy()).isEqualTo("STAFFNO");
    assertThat(body.email()).isEqualTo("member@example.com");
  }

  @Test
  void malformedEnvelopeRemainsRegistrationContractFailure() {
    var executor =
        capturing(new AtomicReference<>(), new AtomicInteger(), new ApimResponseEnvelope<>(null));
    var adapter = new ApimRegistrationLookupAdapter(executor, mock(FailureEventPort.class));
    StepVerifier.create(adapter.searchMember(command(MemberIdentityType.GPFS)))
        .expectErrorSatisfies(
            e -> assertThat(e).isInstanceOf(RegistrationLookupDownstreamException.class))
        .verify();
  }

  private static RegistrationLookupCommand command(MemberIdentityType type) {
    return new RegistrationLookupCommand(
        type,
        "1234567890",
        "31/01/1980",
        RegistrationContactType.EMAIL,
        "member@example.com",
        ActorUserRole.ROLE_USER_MBR);
  }

  private static TrpSearchMemberApimDataItem item(String e, String m, String env, String trust) {
    return new TrpSearchMemberApimDataItem(e, m, env, trust);
  }

  private static ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope(
      String err, List<TrpSearchMemberApimDataItem> data) {
    return new ApimResponseEnvelope<>(new ApimResponseBody<>(err, data));
  }

  private static ApimExchangeExecutor capturing(
      AtomicReference<ApimExchangeRequest<?, ?>> seen,
      AtomicInteger calls,
      ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope) {
    return new ApimExchangeExecutor() {
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        seen.set(request);
        calls.incrementAndGet();
        @SuppressWarnings("unchecked")
        var cast = (ApimResponseEnvelope<D>) (Object) envelope;
        return Mono.just(cast);
      }
    };
  }
}
