package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config.TrpGetAccountListProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TrpGetAccountListAdapterTest {
  private static final LoginAccountAuthorizationCommand COMMAND =
      new LoginAccountAuthorizationCommand(
          "member01",
          "cust-id",
          "member01",
          "req-1",
          com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

  @Test
  void mapsSuccessfulSharedEnvelopeAndPreservesRecordOrder() {
    var first = new TrpGetAccountListApimDataItem("DB", "DBA", "8802", "OE", 1, "", "");
    var second = new TrpGetAccountListApimDataItem("JP", "JPA", "9901", null, 2, "T", "20/07/2026");
    var adapter = adapter(envelope("", List.of(first, second)), properties());

    StepVerifier.create(adapter.getAccounts(COMMAND))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(LoginAccountAuthorizationStatus.AVAILABLE);
              assertThat(result.accounts())
                  .extracting(LoginAccountRecord::accountEnv)
                  .containsExactly("DB", "JP");
            })
        .verifyComplete();
  }

  @Test
  void emptySuccessEnvelopeMapsToUnavailable() {
    StepVerifier.create(adapter(envelope("", List.of()), properties()).getAccounts(COMMAND))
        .assertNext(
            result ->
                assertThat(result.status()).isEqualTo(LoginAccountAuthorizationStatus.UNAVAILABLE))
        .verifyComplete();
  }

  @Test
  void configuredKnownMessagesMapToSafeUnavailableWithoutExposingProgressCode() {
    for (String code : List.of("err.tpa.no.active.account", "err.tpa.no.valid.login.ac")) {
      var result = adapter(envelope(code, null), properties()).getAccounts(COMMAND).block();
      assertThat(result.status()).isEqualTo(LoginAccountAuthorizationStatus.UNAVAILABLE);
      assertThat(result.toString()).doesNotContain(code);
    }
  }

  @Test
  void unknownBusinessMessageUsesSafeUnavailableFallback() {
    var result =
        adapter(envelope("err.tpa.future.code", null), properties()).getAccounts(COMMAND).block();
    assertThat(result.status()).isEqualTo(LoginAccountAuthorizationStatus.UNAVAILABLE);
    assertThat(result.toString()).doesNotContain("err.tpa.future.code");
  }

  private TrpGetAccountListAdapter adapter(
      ApimResponseEnvelope<TrpGetAccountListApimDataItem> response,
      TrpGetAccountListProperties properties) {
    TrpGetAccountListTransport transport = command -> Mono.just(response);
    return new TrpGetAccountListAdapter(
        transport, new TrpGetAccountListContractMapper(), properties);
  }

  private TrpGetAccountListProperties properties() {
    var properties = new TrpGetAccountListProperties();
    properties.setErrorMappings(
        Map.of(
            "err.tpa.no.active.account", "UNAVAILABLE",
            "err.tpa.no.valid.login.ac", "UNAVAILABLE"));
    return properties;
  }

  private ApimResponseEnvelope<TrpGetAccountListApimDataItem> envelope(
      String error, List<TrpGetAccountListApimDataItem> data) {
    var body = new ApimResponseBody<TrpGetAccountListApimDataItem>();
    body.setErrMessage(error);
    body.setData(data);
    var envelope = new ApimResponseEnvelope<TrpGetAccountListApimDataItem>();
    envelope.setResponse(body);
    return envelope;
  }
}
