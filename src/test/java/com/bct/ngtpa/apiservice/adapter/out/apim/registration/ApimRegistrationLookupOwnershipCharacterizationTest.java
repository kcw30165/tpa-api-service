package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseMappingException;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
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
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupInvocationIntent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Verifies Registration TRPSearchMember ownership is scoped to the Start invocation. */
class ApimRegistrationLookupOwnershipCharacterizationTest {
  @Test
  void continuationIntentIsAvailableForCompletionLookupOwnership() {
    assertThat(java.util.Arrays.stream(RegistrationLookupInvocationIntent.values()))
        .extracting(Enum::name)
        .contains("CONTINUATION");
  }

  @Test
  void registrationStartUsesTerminalCallerAndOtherInvocationsKeepObserverOwnership() {
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
    var startCommand =
        new RegistrationLookupCommand(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "member@example.com",
            ActorUserRole.ROLE_USER_MBR,
            RegistrationLookupInvocationIntent.START);
    var otherCommand =
        new RegistrationLookupCommand(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "member@example.com",
            ActorUserRole.ROLE_USER_MBR);
    var continuationCommand =
        new RegistrationLookupCommand(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "member@example.com",
            ActorUserRole.ROLE_USER_MBR,
            RegistrationLookupInvocationIntent.CONTINUATION);

    StepVerifier.create(adapter.searchMember(startCommand)).expectNextCount(1).verifyComplete();
    StepVerifier.create(adapter.searchMember(otherCommand)).expectNextCount(1).verifyComplete();
    StepVerifier.create(adapter.searchMember(continuationCommand))
        .expectNextCount(1)
        .verifyComplete();

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor, org.mockito.Mockito.times(3)).postEnvelope(captor.capture());
    var exchanges = captor.getAllValues();
    assertThat(exchanges).hasSize(3);
    assertThat(exchanges.getFirst().path()).isEqualTo("/ws/NGTPA/v1/TRPSearchMember");
    assertThat(exchanges.getFirst().databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(exchanges.getFirst().incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
    assertThat(exchanges.get(1).incidentOwnership()).isEqualTo(ApimIncidentOwnership.APIM_OBSERVER);
    assertThat(exchanges.get(2).incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
  }

  @Test
  void continuationTechnicalDiagnosticIsWarningClassifiedAndRedactsDownstreamText() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    String downstreamMarker = "continuation-downstream-private-marker-16e2";
    when(executor.postEnvelope(any()))
        .thenReturn(
            Mono.just(
                new ApimResponseEnvelope<>(new ApimResponseBody<>(downstreamMarker, List.of()))));
    List<FailureEvent> failures = new ArrayList<>();
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor,
            new TrpSearchMemberApimMapper(),
            new TrpSearchMemberApimResultMapper(),
            failures::add,
            new MaskingService(new MaskingProperties()));
    var command =
        new RegistrationLookupCommand(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "member@example.com",
            ActorUserRole.ROLE_USER_MBR,
            RegistrationLookupInvocationIntent.CONTINUATION);

    StepVerifier.create(adapter.searchMember(command)).expectError().verify();

    assertThat(command.warningOnlyOnFailure()).isTrue();
    assertThat(failures)
        .singleElement()
        .satisfies(
            failure -> {
              assertThat(failure.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_TECHNICAL);
              assertThat(failure.internalCode()).isEqualTo("UNEXPECTED_ERROR");
              assertThat(failure.safeDimensions().get("reason")).isEqualTo("UNEXPECTED_ERROR");
              assertThat(failure.toString()).doesNotContain(downstreamMarker);
            });
  }

  @Test
  void searchMemberFailureClassificationMapsCategoriesWithoutAnotherExchange() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor,
            new TrpSearchMemberApimMapper(),
            new TrpSearchMemberApimResultMapper(),
            mock(FailureEventPort.class),
            new MaskingService(new MaskingProperties()));

    assertThat(
            adapter.classifySearchMemberFailure(
                new RegistrationLookupDownstreamException(
                    RegistrationLookupDownstreamException.Reason.EMPTY_DATA, null)))
        .isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
    assertThat(
            adapter.classifySearchMemberFailure(
                new ApimException(
                    HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "synthetic")))
        .isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
    assertThat(adapter.classifySearchMemberFailure(new ApimCryptoException("synthetic")))
        .isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    assertThat(
            adapter.classifySearchMemberFailure(
                new ApimResponseMappingException(new IllegalArgumentException("synthetic"))))
        .isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    assertThat(
            adapter.classifySearchMemberFailure(
                new ApimException(
                    HttpStatus.SERVICE_UNAVAILABLE, "err.apim.unavailable", "synthetic")))
        .isEqualTo(FailureOrigin.DOWNSTREAM_TRANSPORT);
    assertThat(adapter.classifySearchMemberFailure(new ApimProfileResolutionException("synthetic")))
        .isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(adapter.classifySearchMemberFailure(new IllegalStateException("synthetic")))
        .isEqualTo(FailureOrigin.LOCAL_SYSTEM);

    verifyNoInteractions(executor);
  }

  @Test
  void defaultInvocationPreservesDownstreamErrorCodeAndLegacyFailureOrigin() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    String downstreamMarker = "default-downstream-private-marker-583a";
    when(executor.postEnvelope(any()))
        .thenReturn(
            Mono.just(
                new ApimResponseEnvelope<>(new ApimResponseBody<>(downstreamMarker, List.of()))));
    List<FailureEvent> failures = new ArrayList<>();
    var adapter =
        new ApimRegistrationLookupAdapter(
            executor,
            new TrpSearchMemberApimMapper(),
            new TrpSearchMemberApimResultMapper(),
            failures::add,
            new MaskingService(new MaskingProperties()));
    var defaultCommand =
        new RegistrationLookupCommand(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "member@example.com",
            ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(adapter.searchMember(defaultCommand)).expectError().verify();

    assertThat(failures)
        .singleElement()
        .satisfies(
            failure -> {
              assertThat(failure.internalCode()).isEqualTo(downstreamMarker);
              assertThat(failure.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
              assertThat(failure.publicCode()).isEqualTo("err.workflow.downstream.unavailable");
              assertThat(failure.httpStatus()).isEqualTo(503);
              assertThat(failure.retryable()).isTrue();
              assertThat(failure.safeDimensions().get("reason")).isEqualTo("UNEXPECTED_ERROR");
            });
  }
}
