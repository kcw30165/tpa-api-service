package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.CancelInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.in.CancelInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceCancelPort;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

public final class CancelInvestmentChoiceService implements CancelInvestmentChoiceUseCase {

  private static final String CAPABILITY = "INVESTMENT_CHOICE";
  private static final String SNAPSHOT_OPERATION = "SNAPSHOT";
  private static final String SNAPSHOT_MISSING_EVENT = "investment-choice.cancel.snapshot.missing";
  private static final String SNAPSHOT_MISSING = "SNAPSHOT_MISSING";
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
    return Mono.defer(
            () ->
                snapshotCache.read(
                    command.authenticatedSessionId(),
                    command.snapshotAccountRef(),
                    command.policyNo(),
                    command.requestId()))
        .defaultIfEmpty(Optional.empty())
        .onErrorResume(error -> Mono.just(Optional.empty()))
        .flatMap(
            snapshot ->
                snapshot
                    .map(value -> cancelAgainstSnapshot(command, value))
                    .orElseGet(() -> snapshotMissing(command)));
  }

  private Mono<InvestmentChoiceCancelOutcome> cancelAgainstSnapshot(
      CancelInvestmentChoiceCommand command, InvestmentChoiceSnapshot snapshot) {
    if (!snapshot.pendingRefNos().contains(command.referenceNo())) {
      return Mono.just(
          new InvestmentChoiceCancelOutcome.Invalid(
              List.of(
                  new InvestmentChoiceViolation(
                      InvestmentChoiceViolation.Kind.FIELD,
                      ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_INVALID,
                      List.of("refNo")))));
    }

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
        .flatMap(
            outcome ->
                Mono.defer(
                        () ->
                            snapshotCache.write(
                                command.authenticatedSessionId(),
                                snapshot.withoutPendingRef(command.referenceNo()),
                                command.requestId()))
                    .onErrorResume(error -> Mono.empty())
                    .thenReturn(outcome));
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

  private Mono<InvestmentChoiceCancelOutcome> snapshotMissing(
      CancelInvestmentChoiceCommand command) {
    try {
      failures.publish(
          new FailureEvent(
              SNAPSHOT_MISSING_EVENT,
              command.requestId(),
              CAPABILITY,
              SNAPSHOT_OPERATION,
              FailureOrigin.LOCAL_SYSTEM,
              SNAPSHOT_MISSING,
              CANCEL_UNAVAILABLE,
              503,
              false,
              Map.of("reason", "missing")));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the cancellation outcome.
    }
    return Mono.error(new InvestmentChoiceCancelUnavailableException());
  }
}
