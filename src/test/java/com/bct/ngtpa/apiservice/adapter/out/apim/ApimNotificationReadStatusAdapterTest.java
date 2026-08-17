package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ApimNotificationReadStatusAdapterTest {
    @Test
    void sendsExactTypedAuthenticatedExchangeExactlyOnceAndMapsValidSuccess() {
        AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        var result = new ApimNotificationReadStatusAdapter(capturing(captured, calls, validEnvelope()))
                .updateReadStatus(command("JP", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block();
        ApimExchangeRequest<?, ?> exchange = captured.get();
        UpdateNotificationReadStatusApimRequest body = (UpdateNotificationReadStatusApimRequest) exchange.request();

        assertThat(calls).hasValue(1);
        assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPUpdMsgRead");
        assertThat(exchange.requestType()).isEqualTo(UpdateNotificationReadStatusApimRequest.class);
        assertThat(exchange.responseDataType()).isEqualTo(UpdateNotificationReadStatusApimDataItem.class);
        assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
        assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
        assertThat(exchange.credentialContext().accountEnv()).isEqualTo("JP");
        assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.IDEMPOTENT_MUTATION);
        assertThat(body.getMsgCodeLong()).isEqualTo("msgCode1,msgCode2");
        assertThat(body.getStatus()).isEqualTo("READ");
        assertThat(body.getAccountEnv()).isEqualTo("JP");
        assertThat(result.notifications()).extracting("msgCode").containsExactly("msgCode1", "msgCode2");
        assertThat(result.notifications()).extracting("isRead").containsExactly(true, false);
    }

    @Test
    void preservesCommandActorRoleAndAccountEnvironmentInExchange() {
        AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        new ApimNotificationReadStatusAdapter(capturing(captured, calls, emptyEnvelope()))
                .updateReadStatus(command("UAT", ActorUserRole.ROLE_USER_EPR, MessageStatus.UNREAD)).block();
        assertThat(captured.get().credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);
        assertThat(captured.get().credentialContext().accountEnv()).isEqualTo("UAT");
        assertThat(calls).hasValue(1);
    }

    @Test
    void blankAccountEnvironmentFailsBeforeExecutorInvocation() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> new ApimNotificationReadStatusAdapter(countingFailure(calls,
                new AssertionError("must not run"))).updateReadStatus(
                        command(" ", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void emptyDataReturnsEmptyResult() {
        var result = new ApimNotificationReadStatusAdapter(fixed(emptyEnvelope()))
                .updateReadStatus(command("JP", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block();
        assertThat(result.notifications()).isEmpty();
    }

    @Test
    void recognizedBusinessRejectionIsPreserved() {
        assertThatThrownBy(() -> new ApimNotificationReadStatusAdapter(fixed(errorEnvelope("APIM update failed")))
                .updateReadStatus(command("JP", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block())
                .isInstanceOf(ApimException.class).hasMessage("APIM update failed")
                .extracting("errorCode").isEqualTo(ErrorCodes.APIM_RESPONSE_INVALID);
    }

    @Test
    void malformedAndStructurallyInvalidEnvelopesAreRejected() {
        assertThatThrownBy(() -> new ApimNotificationReadStatusAdapter(fixed(envelopeWithMissingResponse()))
                .updateReadStatus(command("JP", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block())
                .isInstanceOf(ApimException.class).hasMessageContaining("response is missing");
        assertThatThrownBy(() -> new ApimNotificationReadStatusAdapter(fixed(envelopeWithMissingData()))
                .updateReadStatus(command("JP", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block())
                .isInstanceOf(ApimException.class).hasMessageContaining("response.data is required");
    }

    @Test
    void ordinaryExecutorFailurePropagatesUnchanged() {
        IllegalStateException failure = new IllegalStateException("executor unavailable");
        assertThatThrownBy(() -> new ApimNotificationReadStatusAdapter(
                countingFailure(new AtomicInteger(), failure)).updateReadStatus(
                        command("JP", ActorUserRole.ROLE_USER_MBR, MessageStatus.READ)).block())
                .isSameAs(failure);
    }

    @Test
    void statusAndMessageCodeMappingPreservesFallbackUnknownAndNullFiltering() {
        ArrayList<UpdateNotificationReadStatusApimDataItem> items = new ArrayList<>();
        items.add(null);
        items.add(UpdateNotificationReadStatusApimDataItem.builder()
                .msgCode("fallback-code").msgCodeLong(" ").success(true).build());
        var envelope = ApimResponseEnvelope.<UpdateNotificationReadStatusApimDataItem>builder()
                .response(ApimResponseBody.<UpdateNotificationReadStatusApimDataItem>builder()
                        .errMessage("").data(items).build()).build();
        var result = new ApimNotificationReadStatusAdapter(fixed(envelope))
                .updateReadStatus(command("JP", ActorUserRole.ROLE_USER_MBR, null)).block();
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().getFirst().msgCode()).isEqualTo("fallback-code");
        assertThat(result.notifications().getFirst().status()).isEqualTo(MessageStatus.UNKNOWN);
        assertThat(result.notifications().getFirst().isRead()).isFalse();
    }

    private static ApimExchangeExecutor capturing(AtomicReference<ApimExchangeRequest<?, ?>> captured,
            AtomicInteger calls, ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> response) {
        return new ApimExchangeExecutor() {
            @Override public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> request) {
                captured.set(request); calls.incrementAndGet();
                @SuppressWarnings("unchecked") var result = (ApimResponseEnvelope<D>) (Object) response;
                return Mono.just(result);
            }
        };
    }
    private static ApimExchangeExecutor fixed(ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> response) {
        return capturing(new AtomicReference<>(), new AtomicInteger(), response);
    }
    private static ApimExchangeExecutor countingFailure(AtomicInteger calls, Throwable failure) {
        return new ApimExchangeExecutor() {
            @Override public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> request) {
                calls.incrementAndGet(); return Mono.error(failure);
            }
        };
    }
    private static UpdateNotificationsReadStatusCommand command(String env, ActorUserRole role, MessageStatus status) {
        return new UpdateNotificationsReadStatusCommand(env, role, List.of("msgCode1", "msgCode2"),
                "P1", "C1", "U1", "01/01/2026", status);
    }
    private static ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> validEnvelope() {
        return ApimResponseEnvelope.<UpdateNotificationReadStatusApimDataItem>builder()
                .response(ApimResponseBody.<UpdateNotificationReadStatusApimDataItem>builder().errMessage("")
                        .data(List.of(
                                UpdateNotificationReadStatusApimDataItem.builder().msgCodeLong("msgCode1").success(true).build(),
                                UpdateNotificationReadStatusApimDataItem.builder().msgCodeLong("msgCode2").success(false).build()))
                        .build()).build();
    }
    private static ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> emptyEnvelope() {
        return ApimResponseEnvelope.<UpdateNotificationReadStatusApimDataItem>builder()
                .response(ApimResponseBody.<UpdateNotificationReadStatusApimDataItem>builder()
                        .errMessage("").data(List.of()).build()).build();
    }
    private static ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> errorEnvelope(String message) {
        return ApimResponseEnvelope.<UpdateNotificationReadStatusApimDataItem>builder()
                .response(ApimResponseBody.<UpdateNotificationReadStatusApimDataItem>builder()
                        .errMessage(message).build()).build();
    }
    private static ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> envelopeWithMissingResponse() {
        return ApimResponseEnvelope.<UpdateNotificationReadStatusApimDataItem>builder().build();
    }
    private static ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> envelopeWithMissingData() {
        return ApimResponseEnvelope.<UpdateNotificationReadStatusApimDataItem>builder()
                .response(ApimResponseBody.<UpdateNotificationReadStatusApimDataItem>builder()
                        .errMessage("").build()).build();
    }
}
