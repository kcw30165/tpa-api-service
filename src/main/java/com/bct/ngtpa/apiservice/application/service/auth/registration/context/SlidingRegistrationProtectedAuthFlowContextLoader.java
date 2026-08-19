package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Refreshes the registration AuthFlowSession only after protected-context, CSRF, scenario, expiry,
 * terminal-state, and caller-specific status validation have all succeeded.
 */
public final class SlidingRegistrationProtectedAuthFlowContextLoader
    implements RegistrationProtectedAuthFlowContextLoader {

  private final RegistrationProtectedAuthFlowContextLoader delegate;
  private final AuthFlowSessionExpiryRefreshPort expiryRefreshPort;
  private final Clock clock;

  public SlidingRegistrationProtectedAuthFlowContextLoader(
      RegistrationProtectedAuthFlowContextLoader delegate,
      AuthFlowSessionExpiryRefreshPort expiryRefreshPort,
      Clock clock) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.expiryRefreshPort =
        Objects.requireNonNull(expiryRefreshPort, "expiryRefreshPort must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Mono<RegistrationProtectedAuthFlowContextResult> load(
      RegistrationProtectedAuthFlowContextCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return delegate
        .load(command)
        .flatMap(
            result -> {
              if (!result.isValid()
                  || !command.acceptedStatuses().contains(AuthFlowStatus.OTP_SENT)) {
                return Mono.just(result);
              }
              Instant interactionAt = clock.instant();
              return expiryRefreshPort
                  .refresh(command.authFlowSessionId(), interactionAt)
                  .thenReturn(result);
            });
  }
}
