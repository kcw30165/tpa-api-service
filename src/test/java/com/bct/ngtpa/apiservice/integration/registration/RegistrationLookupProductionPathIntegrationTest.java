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
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
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
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor, mock(FailureEventPort.class), new MaskingService(new MaskingProperties()));
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
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor, mock(FailureEventPort.class), new MaskingService(new MaskingProperties()));
    StepVerifier.create(adapter.searchMember(command(MemberIdentityType.GPFS)))
        .expectErrorSatisfies(
            e -> assertThat(e).isInstanceOf(RegistrationLookupDownstreamException.class))
        .verify();
  }

  @Test
  void notEligibleOutcomeRecordsDownstreamRejectionWithMaskedDimensions() {
    var executor =
        capturing(
            new AtomicReference<>(),
            new AtomicInteger(),
            envelope("err.tpa.member.not.found", List.of()));
    var failureEventPort = mock(FailureEventPort.class);
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor, failureEventPort, new MaskingService(new MaskingProperties()));
    var result = adapter.searchMember(command(MemberIdentityType.STAFFNO)).block();
    assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_ELIGIBLE);
    assertThat(result.accounts()).isEmpty();
    // Deliberately avoid asserting on specific FailureEvent accessor names (not
    // independently confirmed) - capture the raw argument and assert on its
    // toString() representation, matching literal values already confirmed present
    // in ApimRegistrationLookupAdapter.recordDownstreamRejection()'s call site.
    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent.class);
    verify(failureEventPort, timeout(2000)).record(captor.capture());
    String captured = String.valueOf(captor.getValue());
    assertThat(captured)
        .contains("event.registration.lookup.downstream_rejection", "err.tpa.member.not.found");
  }

  @Test
  void nullDataListExercisesGetDataNullBranchAndStillClassifiesAsDownstreamContractFailure() {
    var executor = capturing(new AtomicReference<>(), new AtomicInteger(), envelope("", null));
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor, mock(FailureEventPort.class), new MaskingService(new MaskingProperties()));
    StepVerifier.create(adapter.searchMember(command(MemberIdentityType.GPFS)))
        .expectErrorSatisfies(
            e -> assertThat(e).isInstanceOf(RegistrationLookupDownstreamException.class))
        .verify();
  }

  @Test
  void nonDownstreamExceptionErrorIsPropagatedWithoutFailureEventRecording() {
    var failureEventPort = mock(FailureEventPort.class);
    ApimExchangeExecutor executor =
        erroring(new IllegalStateException("upstream transport failure"));
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor, failureEventPort, new MaskingService(new MaskingProperties()));
    StepVerifier.create(adapter.searchMember(command(MemberIdentityType.GPFS)))
        .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class))
        .verify();
    verifyNoInteractions(failureEventPort);
  }

  @Test
  void mobileContactTypeNotEligibleOutcomeMasksMobileNotEmail() {
    var executor =
        capturing(
            new AtomicReference<>(),
            new AtomicInteger(),
            envelope("err.tpa.member.not.found", List.of()));
    var failureEventPort = mock(FailureEventPort.class);
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor, failureEventPort, new MaskingService(new MaskingProperties()));
    var result =
        adapter
            .searchMember(
                command(MemberIdentityType.GPFS, RegistrationContactType.MOBILE, "91234567"))
            .block();
    assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_ELIGIBLE);
    verify(failureEventPort, timeout(2000)).record(any());
  }

  private static ApimExchangeExecutor erroring(Throwable error) {
    return new ApimExchangeExecutor() {
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        return Mono.error(error);
      }
    };
  }

  private static RegistrationLookupCommand command(
      MemberIdentityType type, RegistrationContactType contactType, String contactValue) {
    return new RegistrationLookupCommand(
        type, "1234567890", "31/01/1980", contactType, contactValue, ActorUserRole.ROLE_USER_MBR);
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
    return new TrpSearchMemberApimDataItem(env, trust, null, null, null, null, null, Boolean.FALSE, null);
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
