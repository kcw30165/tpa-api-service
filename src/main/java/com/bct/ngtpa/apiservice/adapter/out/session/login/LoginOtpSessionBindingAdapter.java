package com.bct.ngtpa.apiservice.adapter.out.session.login;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginOtpSessionBindingCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.LoadAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowDestinationCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowStatusCommand;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class LoginOtpSessionBindingAdapter implements LoginOtpSessionBindingPort {
  private final AuthFlowSessionLifecycleService lifecycle;

  public LoginOtpSessionBindingAdapter(AuthFlowSessionLifecycleService lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  @Override
  public Mono<Void> bind(LoginOtpSessionBindingCommand command) {
    var id = new AuthFlowSessionId(command.authFlowSessionId());
    return lifecycle
        .load(new LoadAuthFlowSessionCommand(id, command.correlationId()))
        .flatMap(
            optional -> {
              var session =
                  optional.orElseThrow(
                      () -> new IllegalStateException("Login OTP session unavailable"));
              if (session.scenario() != AuthFlowScenario.LOGIN
                  || session.status() != command.expectedStatus()
                  || !Objects.equals(
                      session.activeOtpChallengeId(), command.expectedCurrentOtpChallengeId())) {
                return Mono.error(new IllegalStateException("Login OTP binding state mismatch"));
              }
              Mono<Void> destinationUpdate =
                  command.hasSelectedDestination()
                      ? lifecycle
                          .updateDestination(
                              new UpdateAuthFlowDestinationCommand(
                                  id,
                                  command.selectedContactType(),
                                  command.selectedMaskedDestination(),
                                  command.correlationId()))
                          .then()
                      : Mono.empty();
              return destinationUpdate
                  .then(
                      Mono.defer(
                          () ->
                              lifecycle.updateActiveOtpChallenge(
                                  new UpdateAuthFlowOtpCommand(
                                      id, command.newOtpChallengeId(), command.correlationId()))))
                  .then(
                      Mono.defer(
                          () ->
                              command.resultingStatus() == command.expectedStatus()
                                  ? Mono.empty()
                                  : lifecycle
                                      .updateStatus(
                                          new UpdateAuthFlowStatusCommand(
                                              id,
                                              command.resultingStatus(),
                                              command.correlationId()))
                                      .then()));
            })
        .then();
  }
}
