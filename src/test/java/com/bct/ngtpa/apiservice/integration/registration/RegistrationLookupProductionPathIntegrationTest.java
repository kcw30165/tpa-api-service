package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.out.apim.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationLookupProductionPathIntegrationTest {
    private final ApimWebClientFacade webClient = mock(ApimWebClientFacade.class);
    private final ApimCertificateService certificates = mock(ApimCertificateService.class);
    private final ApimPayloadCryptoService crypto = mock(ApimPayloadCryptoService.class);
    private final ApimProperties properties = new ApimProperties();
    private final RecordingFailureEvents failures = new RecordingFailureEvents();
    private final ApimRegistrationLookupAdapter adapter = new ApimRegistrationLookupAdapter(
            webClient, certificates, crypto, properties, failures);

    @BeforeEach
    void reset() {
        org.mockito.Mockito.reset(webClient, certificates, crypto);
        failures.events.clear();
        properties.getEncryption().setEnabled(false);
        when(webClient.post(eq("/ws/NGTPA/v1/TRPSearchUnregMember"), any()))
                .thenReturn(Mono.just("controlled-apim-body"));
        when(crypto.encryptRequest(eq("/ws/NGTPA/v1/TRPSearchUnregMember"), any(),
                eq(TrpSearchUnregMemberApimRequest.class), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    @RegistrationTestCase("TC-023")
    void staffNoIsAcceptedAndProductionAdapterSendsStaff() {
        var validation = new com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator()
                .validate(new RegistrationStartInput(
                        "STAFF_NO", "12345678", "31/01/1980",
                        "member@example.com", null, "cap_123456"));
        assertThat(validation.valid()).isTrue();
        assertThat(validation.command().identityType()).isEqualTo(RegistrationIdentityType.STAFF);

        respond("", List.of(validItem()));
        adapter.searchUnregisteredMember(command(RegistrationIdentityType.STAFF)).block();

        ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
        verify(webClient).post(eq("/ws/NGTPA/v1/TRPSearchUnregMember"), request.capture());
        assertThat(request.getValue()).isInstanceOf(TrpSearchUnregMemberApimRequest.class);
        var apimRequest = (TrpSearchUnregMemberApimRequest) request.getValue();
        assertThat(apimRequest.searchBy()).isEqualTo("STAFF");
        assertThat(apimRequest.searchBy()).isNotEqualTo("STAFF_NO");
    }

    @Test
    @RegistrationTestCase("TC-026")
    void unexpectedApimErrorIsTechnicalFailureWithStructuredEvidence() {
        respond("err.tpa.bad.request", List.of());
        expectFailure(RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, "req-026");
        assertThat(failures.events).singleElement().satisfies(event -> {
            assertThat(event.httpStatus()).isEqualTo(503);
            assertThat(event.retryable()).isTrue();
            assertThat(event.internalCode()).isEqualTo("err.tpa.bad.request");
            assertThat(event.safeDimensions()).containsEntry("reason", "UNEXPECTED_ERROR");
        });
    }

    @Test
    @RegistrationTestCase("TC-027")
    void blankSuccessWithEmptyDataIsTechnicalFailure() {
        respond("", List.of());
        expectFailure(RegistrationLookupDownstreamException.Reason.EMPTY_DATA, "req-027");
    }

    @Test
    @RegistrationTestCase("TC-028")
    void missingResponseEnvelopeIsTechnicalFailure() {
        respondWith(new ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem>(null));
        expectFailure(RegistrationLookupDownstreamException.Reason.EMPTY_DATA, "req-028");
    }

    @Test
    @RegistrationTestCase("TC-029")
    void missingRequiredAccountFieldIsTechnicalFailure() {
        respond("", List.of(new TrpSearchUnregMemberApimDataItem(
                "member@example.com", "91234567", "", "JPM")));
        expectFailure(RegistrationLookupDownstreamException.Reason.INVALID_ACCOUNT_DATA, "req-029");
    }

    @Test
    @RegistrationTestCase("TC-030")
    void contactMismatchIsTechnicalSecurityFailure() {
        respond("", List.of(new TrpSearchUnregMemberApimDataItem(
                "attacker@example.com", "91234567", "OI", "JPM")));
        expectFailure(RegistrationLookupDownstreamException.Reason.CONTACT_MISMATCH, "req-030");
    }

    private void respond(String error, List<TrpSearchUnregMemberApimDataItem> data) {
        respondWith(new ApimResponseEnvelope<>(new ApimResponseBody<>(error, data)));
    }

    private void respondWith(ApimResponseEnvelope<TrpSearchUnregMemberApimDataItem> envelope) {
        doReturn(envelope).when(crypto).decryptResponseEnvelope(
                eq("/ws/NGTPA/v1/TRPSearchUnregMember"),
                eq("controlled-apim-body"),
                eq(TrpSearchUnregMemberApimDataItem.class),
                isNull());
    }

    private void expectFailure(RegistrationLookupDownstreamException.Reason reason, String requestId) {
        StepVerifier.create(adapter.searchUnregisteredMember(command(RegistrationIdentityType.GPFS))
                        .contextWrite(Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, requestId)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RegistrationLookupDownstreamException.class);
                    assertThat(((RegistrationLookupDownstreamException) error).reason()).isEqualTo(reason);
                    assertThat(error.getMessage()).doesNotContain(
                            "member@example.com", "attacker@example.com", "1234567890");
                })
                .verify();
        assertThat(failures.events).singleElement()
                .satisfies(event -> assertThat(event.requestId()).isEqualTo(requestId));
        verify(webClient, times(1)).post(eq("/ws/NGTPA/v1/TRPSearchUnregMember"), any());
    }

    private RegistrationLookupCommand command(RegistrationIdentityType type) {
        return new RegistrationLookupCommand(
                type, "1234567890", "31/01/1980",
                RegistrationContactType.EMAIL, "member@example.com");
    }

    private TrpSearchUnregMemberApimDataItem validItem() {
        return new TrpSearchUnregMemberApimDataItem(
                "member@example.com", "91234567", "OI", "JPM");
    }

    static final class RecordingFailureEvents implements FailureEventPort {
        final List<FailureEvent> events = new ArrayList<>();
        public void record(FailureEvent event) { events.add(event); }
    }
}
