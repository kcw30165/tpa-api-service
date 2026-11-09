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
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Records the current Registration TRPSearchMember invocation ownership for M3b-R0 review. */
class ApimRegistrationLookupOwnershipCharacterizationTest {
  @Test
  void registrationLookupCurrentlyUsesApimObserverOwnership() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenReturn(
            Mono.just(
                new ApimResponseEnvelope<>(
                    new ApimResponseBody<>(
                        "",
                        List.of(
                            new TrpSearchMemberApimDataItem(
                                "DB", "DBA", null, null, null, null, null, false, null))))));

    var adapter =
        new ApimRegistrationLookupAdapter(
            executor,
            new TrpSearchMemberApimMapper(),
            new TrpSearchMemberApimResultMapper(),
            mock(FailureEventPort.class),
            mock(MaskingService.class));
    var command =
        new RegistrationLookupCommand(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "member@example.com",
            ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(adapter.searchMember(command)).expectNextCount(1).verifyComplete();

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor).postEnvelope(captor.capture());
    ApimExchangeRequest<?, ?> exchange = captor.getValue();
    assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPSearchMember");
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(exchange.incidentOwnership()).isEqualTo(ApimIncidentOwnership.APIM_OBSERVER);
  }
}
