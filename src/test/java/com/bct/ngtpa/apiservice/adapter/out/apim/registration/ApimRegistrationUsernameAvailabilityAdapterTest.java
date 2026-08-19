package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityStatus;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimRegistrationUsernameAvailabilityAdapterTest {

  @Test
  void mapsRequestAndCarriesExplicitRoutingMetadataThroughProductionPort() {
    AtomicReference<TrpChkCustIdTransportCommand> captured = new AtomicReference<>();
    TrpChkCustIdTransport transport =
        command -> {
          captured.set(command);
          return Mono.just(TrpChkCustIdApimEnvelope.success());
        };
    var adapter = new ApimRegistrationUsernameAvailabilityAdapter(transport);

    StepVerifier.create(adapter.checkAvailability(command("abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(RegistrationUsernameAvailabilityStatus.AVAILABLE);
              assertThat(result.downstreamErrorCode()).isNull();
            })
        .verifyComplete();

    assertThat(captured.get().request()).isEqualTo(new TrpChkCustIdApimRequest("abc1@"));
    assertThat(captured.get().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(captured.get().correlationId()).isEqualTo("req-1");
  }

  @Test
  void mapsBusinessErrorWithoutChangingRawDownstreamCode() {
    TrpChkCustIdTransport transport =
        command -> Mono.just(TrpChkCustIdApimEnvelope.businessRejected("err.tpa.username.taken"));
    var adapter = new ApimRegistrationUsernameAvailabilityAdapter(transport);

    StepVerifier.create(adapter.checkAvailability(command("abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(RegistrationUsernameAvailabilityStatus.REJECTED);
              assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.username.taken");
            })
        .verifyComplete();
  }

  @Test
  void propagatesTechnicalFailure() {
    TrpChkCustIdTransport transport =
        command -> Mono.error(new IllegalStateException("transport timeout"));
    var adapter = new ApimRegistrationUsernameAvailabilityAdapter(transport);

    StepVerifier.create(adapter.checkAvailability(command("abc1@")))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("transport timeout"))
        .verify();
  }

  @Test
  void redactsUsernameFromCommandAndRequestToString() {
    var command = command("abc1@");
    var request =
        new com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimMapper()
            .toApimRequest(command);
    assertThat(command.toString()).doesNotContain("abc1@");
    assertThat(request.toString()).doesNotContain("abc1@");
  }

  private RegistrationUsernameAvailabilityCommand command(String username) {
    return new RegistrationUsernameAvailabilityCommand(
        new RegistrationSelectedTrust("TL", "ENV1"),
        username,
        "req-1",
        ActorUserRole.ROLE_USER_MBR);
  }
}
