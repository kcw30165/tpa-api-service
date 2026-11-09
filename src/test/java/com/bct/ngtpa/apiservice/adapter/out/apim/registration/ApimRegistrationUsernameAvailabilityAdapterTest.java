package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseMappingException;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityFailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

  @ParameterizedTest
  @MethodSource("technicalFailureCases")
  void mapsFailureCategoryToApplicationOriginAndKeepsDiagnostic(
      Throwable failure,
      ApimFailureCategory category,
      RegistrationUsernameAvailabilityFailureOrigin expectedOrigin) {
    String privateMarker = "PRIVATE-FAILURE-MARKER";
    Logger logger =
        (Logger) LoggerFactory.getLogger(ApimRegistrationUsernameAvailabilityAdapter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      TrpChkCustIdTransport transport = command -> Mono.error(failure);
      var adapter = new ApimRegistrationUsernameAvailabilityAdapter(transport);

      StepVerifier.create(adapter.checkAvailability(command("abc1@")))
          .assertNext(
              result -> {
                assertThat(result.status())
                    .isEqualTo(RegistrationUsernameAvailabilityStatus.DOWNSTREAM_FAILED);
                assertThat(result.failureOrigin()).isEqualTo(expectedOrigin);
              })
          .verifyComplete();

      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                    .isEqualTo(
                        "event=registration.usernameCheck.apimFailureDiagnostic category="
                            + category
                            + " correlationId=req-1")
                    .doesNotContain(privateMarker);
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> technicalFailureCases() {
    String privateMarker = "PRIVATE-FAILURE-MARKER";
    return Stream.of(
        arguments(
            new ApimException(
                HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, privateMarker),
            ApimFailureCategory.DOWNSTREAM_CONTRACT,
            RegistrationUsernameAvailabilityFailureOrigin.APIM_TECHNICAL),
        arguments(
            new ApimResponseMappingException(new IllegalStateException(privateMarker)),
            ApimFailureCategory.DOWNSTREAM_INVALID_RESPONSE,
            RegistrationUsernameAvailabilityFailureOrigin.APIM_TECHNICAL),
        arguments(
            new java.net.ConnectException(privateMarker),
            ApimFailureCategory.DOWNSTREAM_TRANSPORT,
            RegistrationUsernameAvailabilityFailureOrigin.APIM_TECHNICAL),
        arguments(
            new IllegalStateException(privateMarker),
            ApimFailureCategory.LOCAL_SYSTEM,
            RegistrationUsernameAvailabilityFailureOrigin.LOCAL_SYSTEM));
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
