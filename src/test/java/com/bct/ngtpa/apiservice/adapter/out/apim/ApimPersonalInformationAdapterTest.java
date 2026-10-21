package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ApimPersonalInformationAdapterTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void sendsExactTypedAuthenticatedExchangeExactlyOnceAndMapsValidResponse() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();

    var result =
        new ApimMemberInfoAdapter(capturing(captured, calls, validEnvelope()))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_EPR))
            .block();
    ApimExchangeRequest<?, ?> exchange = captured.get();
    GetMemberInfoApimRequest body = (GetMemberInfoApimRequest) exchange.request();

    assertThat(calls).hasValue(1);
    assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPGetMemberInfo");
    assertThat(exchange.requestType()).isEqualTo(GetMemberInfoApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(GetMemberInfoDataItem.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(body.getPolicyNo()).isEqualTo("POL-1");
    assertThat(body.getCertNo()).isEqualTo("CERT-1");
    assertThat(body.getLoginUserId()).isEqualTo("USER-1");
    assertThat(result.getPayload()).containsOnlyKeys("config", "data");
    Map<?, ?> config = (Map<?, ?>) result.getPayload().get("config");
    Map<?, ?> data = (Map<?, ?>) result.getPayload().get("data");
    assertThat(config.get("addr1")).isEqualTo("EDITABLE_COM");
    assertThat(data.get("addr1")).isEqualTo("1 Example Street");
  }

  @Test
  void serializedRequestContainsExactTrpGetMemberInfoFieldsWithoutEnvAndNoFrontendAuthority()
      throws Exception {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    new ApimMemberInfoAdapter(capturing(captured, new AtomicInteger(), emptyEnvelope()))
        .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
        .block();

    var node = JSON.readTree(JSON.writeValueAsString(captured.get().request()));
    assertThat(node.get("policy-no").asText()).isEqualTo("POL-1");
    assertThat(node.get("cert-no").asText()).isEqualTo("CERT-1");
    assertThat(node.get("login-user-id").asText()).isEqualTo("USER-1");
    assertThat(node.get("login-user-role").asText()).isEqualTo("role_user_mbr");
    assertThat(node.size()).isEqualTo(4);
    assertThat(node.has("env")).isFalse();
    assertThat(node.has("accountEnv")).isFalse();
    assertThat(node.has("accountRef")).isFalse();
    assertThat(node.has("actorUserRole")).isFalse();
    assertThat(node.has("databaseScope")).isFalse();
    assertThat(node.has("retryEligibility")).isFalse();
    assertThat(node.has("user-id")).isFalse();
    assertThat(node.has("user-role")).isFalse();
    assertThat(node.has("mbr-type")).isFalse();
  }

  @Test
  void preservesCommandActorRoleAndAccountEnvironmentInExchange() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    new ApimMemberInfoAdapter(capturing(captured, calls, emptyEnvelope()))
        .fetchMemberInfo(command("UAT", ActorUserRole.ROLE_USER_EPR))
        .block();

    assertThat(captured.get().credentialContext().actorUserRole())
        .isEqualTo(ActorUserRole.ROLE_USER_EPR);
    assertThat(captured.get().credentialContext().accountEnv()).isEqualTo("UAT");
    assertThat(calls).hasValue(1);
  }

  @Test
  void blankAccountEnvironmentFailsBeforeExecutorInvocation() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                new ApimMemberInfoAdapter(
                        countingFailure(calls, new AssertionError("must not run")))
                    .fetchMemberInfo(command(" ", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ACCOUNT_ENV");
    assertThat(calls).hasValue(0);
  }

  @Test
  void emptyNoRecordResponseReturnsEmptyPayload() {
    var result =
        new ApimMemberInfoAdapter(fixed(emptyEnvelope()))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    assertThat(result.getPayload()).isEmpty();
  }

  @Test
  void businessRejectionRemainsFeatureOwned() {
    assertThatThrownBy(
            () ->
                new ApimMemberInfoAdapter(fixed(errorEnvelope("APIM member lookup failed")))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessage("APIM member lookup failed")
        .extracting("errorCode")
        .isEqualTo(ErrorCodes.APIM_RESPONSE_INVALID);
  }

  @Test
  void malformedAndStructurallyInvalidEnvelopesRemainFeatureOwned() {
    assertThatThrownBy(
            () ->
                new ApimMemberInfoAdapter(fixed(envelopeWithMissingResponse()))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response is missing");
    assertThatThrownBy(
            () ->
                new ApimMemberInfoAdapter(fixed(envelopeWithMissingData()))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response.data is required");
  }

  @Test
  void ordinaryExecutorFailurePropagatesUnchanged() {
    IllegalStateException failure = new IllegalStateException("executor unavailable");
    assertThatThrownBy(
            () ->
                new ApimMemberInfoAdapter(countingFailure(new AtomicInteger(), failure))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isSameAs(failure);
  }

  @Test
  void nullConfigDataAndItemsPreserveDefensiveMappingBehavior() {
    ArrayList<MemberInfoConfigItem> config = new ArrayList<>();
    config.add(null);
    config.add(MemberInfoConfigItem.builder().itemId(null).configValue("ignored").build());
    config.add(MemberInfoConfigItem.builder().itemId("email").configValue("READONLY").build());
    ArrayList<Map<String, Object>> data = new ArrayList<>();
    data.add(null);
    var envelope = success(GetMemberInfoDataItem.builder().config(config).data(data).build());

    var result =
        new ApimMemberInfoAdapter(fixed(envelope))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    Map<?, ?> mappedConfig = (Map<?, ?>) result.getPayload().get("config");
    assertThat(mappedConfig).hasSize(1);
    assertThat(mappedConfig.containsKey("email")).isTrue();
    assertThat((Map<?, ?>) result.getPayload().get("data")).isEmpty();

    var nullItemResult =
        new ApimMemberInfoAdapter(fixed(success((GetMemberInfoDataItem) null)))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    assertThat((Map<?, ?>) nullItemResult.getPayload().get("config")).isEmpty();
    assertThat((Map<?, ?>) nullItemResult.getPayload().get("data")).isEmpty();
  }

  private static FetchMemberInfoCommand command(String env, ActorUserRole role) {
    return new FetchMemberInfoCommand(env, "POL-1", "CERT-1", "USER-1", role);
  }

  private static ApimExchangeExecutor capturing(
      AtomicReference<ApimExchangeRequest<?, ?>> captured,
      AtomicInteger calls,
      ApimResponseEnvelope<GetMemberInfoDataItem> response) {
    return new ApimExchangeExecutor() {
      @Override
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        captured.set(request);
        calls.incrementAndGet();
        @SuppressWarnings("unchecked")
        ApimResponseEnvelope<D> result = (ApimResponseEnvelope<D>) (Object) response;
        return Mono.just(result);
      }
    };
  }

  private static ApimExchangeExecutor fixed(ApimResponseEnvelope<GetMemberInfoDataItem> response) {
    return capturing(new AtomicReference<>(), new AtomicInteger(), response);
  }

  private static ApimExchangeExecutor countingFailure(AtomicInteger calls, Throwable failure) {
    return new ApimExchangeExecutor() {
      @Override
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        calls.incrementAndGet();
        return Mono.error(failure);
      }
    };
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> validEnvelope() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("addr1", "1 Example Street");
    return success(
        GetMemberInfoDataItem.builder()
            .config(
                List.of(
                    MemberInfoConfigItem.builder()
                        .itemId("addr1")
                        .configValue("EDITABLE_COM")
                        .build()))
            .data(List.of(data))
            .build());
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> success(GetMemberInfoDataItem item) {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(
            ApimResponseBody.<GetMemberInfoDataItem>builder()
                .errMessage("")
                .data(java.util.Collections.singletonList(item))
                .build())
        .build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> emptyEnvelope() {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(
            ApimResponseBody.<GetMemberInfoDataItem>builder()
                .errMessage("")
                .data(List.of())
                .build())
        .build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> errorEnvelope(String message) {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(ApimResponseBody.<GetMemberInfoDataItem>builder().errMessage(message).build())
        .build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> envelopeWithMissingResponse() {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder().build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> envelopeWithMissingData() {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(ApimResponseBody.<GetMemberInfoDataItem>builder().errMessage("").build())
        .build();
  }
}
