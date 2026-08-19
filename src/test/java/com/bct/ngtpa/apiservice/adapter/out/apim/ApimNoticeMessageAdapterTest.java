package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardMessageItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

class ApimNoticeMessageAdapterTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void sendsExactTypedAuthenticatedExchangeExactlyOnceAndMapsValidResponse() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    ApimNoticeMessageAdapter adapter =
        new ApimNoticeMessageAdapter(capturing(captured, calls, validEnvelope()));

    var result = adapter.fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR)).block();
    ApimExchangeRequest<?, ?> exchange = captured.get();
    GetMessageBoardApimRequest body = (GetMessageBoardApimRequest) exchange.request();

    assertThat(calls).hasValue(1);
    assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPGetMsgBoard");
    assertThat(exchange.requestType()).isEqualTo(GetMessageBoardApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(GetMessageBoardDataItem.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(body.getAccountEnv()).isEqualTo("JP");
    assertThat(body.getLoginUserRole()).isEqualTo("role_user_mbr");
    assertThat(result.notifications()).hasSize(1);
    assertThat(result.notifications().getFirst().msgCode()).isEqualTo("SHORT-001");
    assertThat(result.notifications().getFirst().msgCodeLong()).isEqualTo("LONG-001");
    assertThat(result.notifications().getFirst().category()).isEqualTo("IMP_NOTE");
    assertThat(result.notifications().getFirst().msgTitle()).isEqualTo("IMPORTANT NOTICE");
    assertThat(result.notifications().getFirst().isRead()).isTrue();
    assertThat(result.notifications().getFirst().startDatetime())
        .isEqualTo(LocalDateTime.of(2026, 4, 29, 14, 15));
  }

  @Test
  void preservesCommandActorRoleAndAccountEnvironmentInExchange() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    new ApimNoticeMessageAdapter(capturing(captured, calls, emptyEnvelope()))
        .fetchNotifications(command("UAT", ActorUserRole.ROLE_USER_EPR))
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
                new ApimNoticeMessageAdapter(
                        countingFailure(calls, new AssertionError("must not run")))
                    .fetchNotifications(command(" ", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls).hasValue(0);
  }

  @Test
  void validRequestPayloadExcludesPageAndSizeAndKeepsEnvJsonContract() throws Exception {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    new ApimNoticeMessageAdapter(capturing(captured, new AtomicInteger(), emptyEnvelope()))
        .fetchNotifications(command("DEV", ActorUserRole.ROLE_USER_MBR))
        .block();
    var node = JSON.readTree(JSON.writeValueAsString(captured.get().request()));
    assertThat(node.get("env").asText()).isEqualTo("DEV");
    assertThat(node.get("login-user-role").asText()).isEqualTo("role_user_mbr");
    assertThat(node.has("page")).isFalse();
    assertThat(node.has("size")).isFalse();
  }

  @Test
  void emptyNoRecordResponseReturnsEmptyResult() {
    var result =
        new ApimNoticeMessageAdapter(fixed(emptyEnvelope()))
            .fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    assertThat(result.notifications()).isEmpty();
  }

  @Test
  void downstreamBusinessRejectionIsPreserved() {
    assertThatThrownBy(
            () ->
                new ApimNoticeMessageAdapter(fixed(errorEnvelope("APIM failed")))
                    .fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessage("APIM failed")
        .extracting("errorCode")
        .isEqualTo(ErrorCodes.APIM_RESPONSE_INVALID);
  }

  @Test
  void malformedAndStructurallyInvalidEnvelopesAreRejected() {
    assertThatThrownBy(
            () ->
                new ApimNoticeMessageAdapter(fixed(envelopeWithMissingResponse()))
                    .fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response is missing");
    assertThatThrownBy(
            () ->
                new ApimNoticeMessageAdapter(fixed(envelopeWithMissingData()))
                    .fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response.data is required");
  }

  @Test
  void ordinaryExecutorFailurePropagatesUnchanged() {
    IllegalStateException failure = new IllegalStateException("executor unavailable");
    assertThatThrownBy(
            () ->
                new ApimNoticeMessageAdapter(countingFailure(new AtomicInteger(), failure))
                    .fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isSameAs(failure);
  }

  @Test
  void parsesBothSupportedDatetimeFormatsAndTreatsBlankOrMalformedAsNull() {
    ApimNoticeMessageAdapter adapter = new ApimNoticeMessageAdapter(fixed(emptyEnvelope()));
    assertThat(
            (LocalDateTime)
                ReflectionTestUtils.invokeMethod(adapter, "parseDateTime", "29/04/2026 14:15:00"))
        .isEqualTo(LocalDateTime.of(2026, 4, 29, 14, 15));
    assertThat(
            (LocalDateTime)
                ReflectionTestUtils.invokeMethod(adapter, "parseDateTime", "11/10/2023 00:00"))
        .isEqualTo(LocalDateTime.of(2023, 10, 11, 0, 0));
    assertThat((LocalDateTime) ReflectionTestUtils.invokeMethod(adapter, "parseDateTime", " "))
        .isNull();
    assertThat(
            (LocalDateTime) ReflectionTestUtils.invokeMethod(adapter, "parseDateTime", "bad-date"))
        .isNull();
  }

  @Test
  void ignoresNullEnvelopeItemsAndPreservesUnknownMessageMapping() {
    ArrayList<GetMessageBoardDataItem> data = new ArrayList<>();
    data.add(null);
    data.add(GetMessageBoardDataItem.builder().message(null).build());
    ArrayList<GetMessageBoardMessageItem> messages = new ArrayList<>();
    messages.add(null);
    messages.add(message("custom-type", "U", "bad-date"));
    data.add(GetMessageBoardDataItem.builder().message(messages).build());
    var envelope =
        ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
            .response(
                ApimResponseBody.<GetMessageBoardDataItem>builder()
                    .errMessage("")
                    .data(data)
                    .build())
            .build();
    var result =
        new ApimNoticeMessageAdapter(fixed(envelope))
            .fetchNotifications(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    assertThat(result.notifications()).hasSize(1);
    assertThat(result.notifications().getFirst().msgTitle()).isEqualTo("custom-type");
    assertThat(result.notifications().getFirst().msgStatus()).isEqualTo(MessageStatus.UNREAD);
    assertThat(result.notifications().getFirst().startDatetime()).isNull();
  }

  private static ApimExchangeExecutor capturing(
      AtomicReference<ApimExchangeRequest<?, ?>> captured,
      AtomicInteger calls,
      ApimResponseEnvelope<GetMessageBoardDataItem> response) {
    return new ApimExchangeExecutor() {
      @Override
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        captured.set(request);
        calls.incrementAndGet();
        @SuppressWarnings("unchecked")
        var result = (ApimResponseEnvelope<D>) (Object) response;
        return Mono.just(result);
      }
    };
  }

  private static ApimExchangeExecutor fixed(
      ApimResponseEnvelope<GetMessageBoardDataItem> response) {
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

  private static GetNotificationsCommand command(String env, ActorUserRole role) {
    return new GetNotificationsCommand(
        env, role, 2, 25, "yyyy-MM-dd", "Asia/Hong_Kong", "P1", "C1", "U1", "29/04/2026");
  }

  private static GetMessageBoardMessageItem message(
      String category, String status, String datetime) {
    return GetMessageBoardMessageItem.builder()
        .msgCode("SHORT-001")
        .msgCodeLong("LONG-001")
        .seq(7)
        .msgCate(category)
        .msgTitle("ignored")
        .msgContentChi("chi")
        .msgContentEng("eng")
        .startDatetime(datetime)
        .msgStatus(status)
        .build();
  }

  private static ApimResponseEnvelope<GetMessageBoardDataItem> validEnvelope() {
    return ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
        .response(
            ApimResponseBody.<GetMessageBoardDataItem>builder()
                .errMessage("")
                .data(
                    List.of(
                        GetMessageBoardDataItem.builder()
                            .message(List.of(message("IMP_NOTE", "R", "29/04/2026 14:15:00")))
                            .build()))
                .build())
        .build();
  }

  private static ApimResponseEnvelope<GetMessageBoardDataItem> emptyEnvelope() {
    return ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
        .response(
            ApimResponseBody.<GetMessageBoardDataItem>builder()
                .errMessage("")
                .data(List.of())
                .build())
        .build();
  }

  private static ApimResponseEnvelope<GetMessageBoardDataItem> errorEnvelope(String message) {
    return ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
        .response(ApimResponseBody.<GetMessageBoardDataItem>builder().errMessage(message).build())
        .build();
  }

  private static ApimResponseEnvelope<GetMessageBoardDataItem> envelopeWithMissingResponse() {
    return ApimResponseEnvelope.<GetMessageBoardDataItem>builder().build();
  }

  private static ApimResponseEnvelope<GetMessageBoardDataItem> envelopeWithMissingData() {
    return ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
        .response(ApimResponseBody.<GetMessageBoardDataItem>builder().errMessage("").build())
        .build();
  }
}
