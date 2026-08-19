package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.exception.ApimException;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ApimReferenceDataCountriesAdapterTest {
  @Test
  void sendsExactTypedAuthenticatedExchangeAndMapsOrder() {
    AtomicReference<ApimExchangeRequest<?, ?>> cap = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    var r =
        new ApimReferenceDataCountriesAdapter(capturing(cap, calls, sample()))
            .fetchCountryList(command("JP", ActorUserRole.ROLE_USER_EPR))
            .block();
    var x = cap.get();
    assertThat(calls).hasValue(1);
    assertThat(x.path()).isEqualTo("/ws/NGTPA/v1/TRPGetCountryList");
    assertThat(x.request()).isInstanceOf(GetCountryListApimRequest.class);
    assertThat(x.requestType()).isEqualTo(GetCountryListApimRequest.class);
    assertThat(x.responseDataType()).isEqualTo(GetCountryListDataItem.class);
    assertThat(x.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(x.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);
    assertThat(x.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(x.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(r).extracting(ReferenceDataCountryItem::countryCode).containsExactly("HKG", "CHN");
  }

  @Test
  void validRequestIsEmptyAndExcludesFrontendAuthority() throws Exception {
    AtomicReference<ApimExchangeRequest<?, ?>> cap = new AtomicReference<>();
    new ApimReferenceDataCountriesAdapter(capturing(cap, new AtomicInteger(), empty()))
        .fetchCountryList(command("JP", ActorUserRole.ROLE_USER_MBR))
        .block();
    String json =
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cap.get().request());
    assertThat(json)
        .isEqualTo("{}")
        .doesNotContain("accountRef", "accountEnv", "actorUserRole", "Accept-Language");
  }

  @Test
  void blankEnvironmentFailsBeforeExecutor() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                new ApimReferenceDataCountriesAdapter(failing(calls, new AssertionError()))
                    .fetchCountryList(command(" ", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls).hasValue(0);
  }

  @Test
  void emptyNoRecordIsPreserved() {
    assertThat(
            new ApimReferenceDataCountriesAdapter(fixed(empty()))
                .fetchCountryList(command("JP", ActorUserRole.ROLE_USER_MBR))
                .block())
        .isEmpty();
  }

  @Test
  void businessAndMalformedEnvelopesRemainFeatureOwned() {
    assertThatThrownBy(
            () ->
                new ApimReferenceDataCountriesAdapter(fixed(error("APIM failed")))
                    .fetchCountryList(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessage("APIM failed");
    assertThatThrownBy(
            () ->
                new ApimReferenceDataCountriesAdapter(
                        fixed(ApimResponseEnvelope.<GetCountryListDataItem>builder().build()))
                    .fetchCountryList(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response is missing");
  }

  @Test
  void ordinaryExecutorFailurePropagates() {
    var f = new IllegalStateException("executor unavailable");
    assertThatThrownBy(
            () ->
                new ApimReferenceDataCountriesAdapter(failing(new AtomicInteger(), f))
                    .fetchCountryList(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isSameAs(f);
  }

  static GetReferenceDataCountriesCommand command(String e, ActorUserRole r) {
    return new GetReferenceDataCountriesCommand("en", e, r);
  }

  static ApimExchangeExecutor fixed(ApimResponseEnvelope<GetCountryListDataItem> e) {
    return capturing(new AtomicReference<>(), new AtomicInteger(), e);
  }

  static ApimExchangeExecutor failing(AtomicInteger c, Throwable f) {
    return new ApimExchangeExecutor() {
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> r) {
        c.incrementAndGet();
        return Mono.error(f);
      }
    };
  }

  static ApimExchangeExecutor capturing(
      AtomicReference<ApimExchangeRequest<?, ?>> c,
      AtomicInteger n,
      ApimResponseEnvelope<GetCountryListDataItem> e) {
    return new ApimExchangeExecutor() {
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> r) {
        c.set(r);
        n.incrementAndGet();
        @SuppressWarnings("unchecked")
        var x = (ApimResponseEnvelope<D>) (Object) e;
        return Mono.just(x);
      }
    };
  }

  static ApimResponseEnvelope<GetCountryListDataItem> sample() {
    return ApimResponseEnvelope.<GetCountryListDataItem>builder()
        .response(
            ApimResponseBody.<GetCountryListDataItem>builder()
                .errMessage("")
                .data(
                    List.of(
                        item("HKG", "Hong Kong", "香港", "852"), item("CHN", "China", "中國", "86")))
                .build())
        .build();
  }

  static ApimResponseEnvelope<GetCountryListDataItem> empty() {
    return ApimResponseEnvelope.<GetCountryListDataItem>builder()
        .response(
            ApimResponseBody.<GetCountryListDataItem>builder()
                .errMessage("")
                .data(List.of())
                .build())
        .build();
  }

  static ApimResponseEnvelope<GetCountryListDataItem> error(String m) {
    return ApimResponseEnvelope.<GetCountryListDataItem>builder()
        .response(ApimResponseBody.<GetCountryListDataItem>builder().errMessage(m).build())
        .build();
  }

  static GetCountryListDataItem item(String c, String e, String z, String p) {
    return GetCountryListDataItem.builder()
        .countryCode(c)
        .countryNameEng(e)
        .countryNameChi(z)
        .callingCode(p)
        .build();
  }
}
