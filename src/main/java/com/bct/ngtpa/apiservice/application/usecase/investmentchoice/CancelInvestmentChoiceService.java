package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.CancelInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.in.CancelInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceCancelPort;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

public final class CancelInvestmentChoiceService implements CancelInvestmentChoiceUseCase {

  private static final String CAPABILITY = "INVESTMENT_CHOICE";
  private static final String CANCEL_OPERATION = "CANCEL";
  private static final String CANCEL_EMPTY_RESULT_EVENT = "investment-choice.cancel.empty_result";
  private static final String CANCEL_EMPTY_RESULT = "EMPTY_RESULT";
  private static final String CANCEL_UNAVAILABLE = "err.investment-choice.cancel.unavailable";

  private final InvestmentChoiceSnapshotCacheService snapshotCache;
  private final InvestmentChoiceCancelPort cancelPort;
  private final FailureEventPort failures;

  public CancelInvestmentChoiceService(
      InvestmentChoiceSnapshotCacheService snapshotCache,
      InvestmentChoiceCancelPort cancelPort,
      FailureEventPort failures) {
    this.snapshotCache = Objects.requireNonNull(snapshotCache, "snapshotCache must not be null");
    this.cancelPort = Objects.requireNonNull(cancelPort, "cancelPort must not be null");
    this.failures = Objects.requireNonNull(failures, "failures must not be null");
  }

  @Override
  public Mono<InvestmentChoiceCancelOutcome> cancel(CancelInvestmentChoiceCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    InvestmentChoiceCancelCommand cancelCommand =
        new InvestmentChoiceCancelCommand(
            command.actorUserId(),
            command.actorUserRole(),
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.referenceNo());
    return Mono.defer(() -> cancelPort.cancel(cancelCommand))
        .switchIfEmpty(Mono.defer(() -> emptyCancelResult(command)))
        .<InvestmentChoiceCancelOutcome>map(InvestmentChoiceCancelOutcome.Cancelled::new)
        .flatMap(outcome -> cleanupSnapshot(command).thenReturn(outcome));
  }

  private Mono<Void> cleanupSnapshot(CancelInvestmentChoiceCommand command) {
    return Mono.defer(
            () ->
                snapshotCache.readState(
                    command.authenticatedSessionId(),
                    command.snapshotAccountRef(),
                    command.policyNo(),
                    command.requestId()))
        .defaultIfEmpty(Optional.empty())
        .onErrorResume(error -> Mono.just(Optional.empty()))
        .flatMap(
            state ->
                state
                    .flatMap(InvestmentChoiceSnapshotState::normalSnapshot)
                    .map(
                        snapshot ->
                            Mono.defer(
                                    () ->
                                        snapshotCache.write(
                                            command.authenticatedSessionId(),
                                            snapshot.withoutPendingRef(command.referenceNo()),
                                            command.requestId()))
                                .onErrorResume(error -> Mono.empty()))
                    .orElseGet(Mono::empty));
  }

  private Mono<InvestmentChoiceCancellation> emptyCancelResult(
      CancelInvestmentChoiceCommand command) {
    try {
      failures.publish(
          new FailureEvent(
              CANCEL_EMPTY_RESULT_EVENT,
              command.requestId(),
              CAPABILITY,
              CANCEL_OPERATION,
              FailureOrigin.LOCAL_SYSTEM,
              CANCEL_EMPTY_RESULT,
              CANCEL_UNAVAILABLE,
              503,
              false,
              Map.of("reason", "empty")));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the cancellation outcome.
    }
    return Mono.error(new InvestmentChoiceCancelUnavailableException());
  }
}
