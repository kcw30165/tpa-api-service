package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimRegistrationCompletionAdapterTest {
  @Test
  void usesTypedProtectedAccountEnvNonRetryableExchange() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(
            i ->
                Mono.just(
                    new ApimResponseEnvelope<>(
                        new ApimResponseBody<>(
                            "",
                            List.of(
                                new TrpRegMemberApimDataItem(
                                    true, "db02", "08/07/2026", "16:49:23"))))));
    var adapter = new ApimRegistrationCompletionAdapter(executor);
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(RegistrationCompletionResult.Status.REGISTERED))
        .verifyComplete();
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<ApimExchangeRequest> c = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor).postEnvelope(c.capture());
    ApimExchangeRequest<?, ?> x = c.getValue();
    assertThat(x.path()).isEqualTo("/ws/NGTPA/v1/TRPRegMember");
    assertThat(x.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(x.credentialContext().accountEnv()).isEqualTo("DB");
    assertThat(x.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(x.retryEligibility()).isEqualTo(ApimRetryEligibility.NON_RETRYABLE);
  }

  private RegistrationCompletionCommand command() {
    return new RegistrationCompletionCommand(
        "DB",
        "DBA",
        "db02",
        "3011825",
        "Password1!",
        "15/07/1969",
        "66290597",
        "",
        "req-1",
        ActorUserRole.ROLE_USER_MBR);
  }
}
