package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.FetchReferenceDateCommand;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class ApimReferenceDateRefreshAdapterTest {
  @Test
  void validRequestUsesExactTypedAccountExchangeAndMapsDate() throws Exception {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    org.mockito.Mockito.doReturn(
            Mono.just(
                envelope(
                    "",
                    List.of(GetWebSysDateApimDataItem.builder().sysDate("31/12/2025").build()))))
        .when(executor)
        .postEnvelope(any());
    var result =
        new ApimReferenceDateRefreshAdapter(executor)
            .fetchReferenceDate(new FetchReferenceDateCommand("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor).postEnvelope(captor.capture());
    var request = captor.getValue();
    assertThat(request.path()).isEqualTo("/ws/NGTPA/v1/TRPGetWebSysDate");
    assertThat(request.requestType()).isEqualTo(GetWebSysDateApimRequest.class);
    assertThat(request.responseDataType()).isEqualTo(GetWebSysDateApimDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(request.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(new ObjectMapper().writeValueAsString(request.request()))
        .isEqualTo("{\"env\":\"JP\"}");
    assertThat(result).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  void blankEnvironmentFailsBeforeExecutor() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    assertThatThrownBy(() -> new FetchReferenceDateCommand(" ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class);
    verify(executor, never()).postEnvelope(any());
  }

  @Test
  void nullRoleFailsBeforeExecutor() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    assertThatThrownBy(() -> new FetchReferenceDateCommand("JP", null))
        .isInstanceOf(NullPointerException.class);
    verify(executor, never()).postEnvelope(any());
  }

  @Test
  void rejectionMissingDataAndInvalidDateRemainRejected() {
    assertFailure(envelope("failed", List.of()));
    assertFailure(envelope("", List.of()));
    assertFailure(
        envelope("", List.of(GetWebSysDateApimDataItem.builder().sysDate("2025-12-31").build())));
  }

  private void assertFailure(ApimResponseEnvelope<GetWebSysDateApimDataItem> response) {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    org.mockito.Mockito.doReturn(Mono.just(response)).when(executor).postEnvelope(any());
    assertThatThrownBy(
            () ->
                new ApimReferenceDateRefreshAdapter(executor)
                    .fetchReferenceDate(
                        new FetchReferenceDateCommand("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class);
  }

  private static ApimResponseEnvelope<GetWebSysDateApimDataItem> envelope(
      String error, List<GetWebSysDateApimDataItem> data) {
    return ApimResponseEnvelope.<GetWebSysDateApimDataItem>builder()
        .response(
            ApimResponseBody.<GetWebSysDateApimDataItem>builder()
                .errMessage(error)
                .data(data)
                .build())
        .build();
  }
}
