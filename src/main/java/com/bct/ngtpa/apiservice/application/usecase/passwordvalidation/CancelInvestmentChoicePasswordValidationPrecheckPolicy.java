package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelValidationResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceCancelPrecheckValidator;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

public final class CancelInvestmentChoicePasswordValidationPrecheckPolicy
    implements PasswordValidationPurposePrecheckPolicy<
        CancelInvestmentChoicePasswordValidationPayload> {

  private static final String SNAPSHOT_MISSING_EVENT = "investment-choice.cancel.snapshot.missing";
  private static final String SNAPSHOT_MISSING = "SNAPSHOT_MISSING";

  private final InvestmentChoiceSnapshotCacheService snapshotCache;
  private final FailureEventPort failures;
  private final InvestmentChoiceCancelPrecheckValidator cancelValidator =
      new InvestmentChoiceCancelPrecheckValidator();

  public CancelInvestmentChoicePasswordValidationPrecheckPolicy(
      InvestmentChoiceSnapshotCacheService snapshotCache, FailureEventPort failures) {
    this.snapshotCache = Objects.requireNonNull(snapshotCache, "snapshotCache must not be null");
    this.failures = Objects.requireNonNull(failures, "failures must not be null");
  }

  @Override
  public PasswordValidationPurpose purpose() {
    return PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE;
  }

  @Override
  public Class<CancelInvestmentChoicePasswordValidationPayload> payloadType() {
    return CancelInvestmentChoicePasswordValidationPayload.class;
  }

  @Override
  public Mono<PasswordValidationPrecheckResult> precheck(
      CancelInvestmentChoicePasswordValidationPayload payload,
      PasswordValidationPrecheckContext context) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(context, "context must not be null");

    InvestmentChoiceCancelValidationResult cancel = cancelValidator.validate(payload);
    if (!cancel.isValid()) {
      return Mono.just(rejected(cancel.violations()));
    }

    AccountContext account = Objects.requireNonNull(context.context().account());
    return Mono.defer(
            () ->
                snapshotCache.read(
                    context.sessionId(),
                    account.accountRef(),
                    account.policyNo(),
                    context.correlationId()))
        .defaultIfEmpty(Optional.empty())
        .onErrorResume(error -> Mono.just(Optional.empty()))
        .flatMap(snapshot -> precheckSnapshot(cancel.referenceNo(), snapshot, context));
  }

  private Mono<PasswordValidationPrecheckResult> precheckSnapshot(
      String referenceNo,
      Optional<InvestmentChoiceSnapshot> snapshot,
      PasswordValidationPrecheckContext context) {
    if (snapshot.isEmpty()) {
      return snapshotMissing(context);
    }
    if (!snapshot.get().pendingRefNos().contains(referenceNo)) {
      return Mono.just(
          rejected(
              List.of(
                  new InvestmentChoiceViolation(
                      InvestmentChoiceViolation.Kind.FIELD,
                      ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_INVALID,
                      List.of("refNo")))));
    }
    return Mono.just(PasswordValidationPrecheckResult.success());
  }

  private Mono<PasswordValidationPrecheckResult> snapshotMissing(
      PasswordValidationPrecheckContext context) {
    try {
      failures.publish(
          new FailureEvent(
              SNAPSHOT_MISSING_EVENT,
              context.correlationId(),
              "INVESTMENT_CHOICE",
              "SNAPSHOT",
              FailureOrigin.LOCAL_SYSTEM,
              SNAPSHOT_MISSING,
              ErrorCodes.INVESTMENT_CHOICE_CANCEL_UNAVAILABLE,
              503,
              false,
              Map.of("reason", "missing")));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the precheck outcome.
    }
    return Mono.error(new InvestmentChoiceCancelUnavailableException());
  }

  private static PasswordValidationPrecheckResult rejected(
      List<InvestmentChoiceViolation> violations) {
    return PasswordValidationPrecheckResult.rejected(
        violations.stream()
            .map(CancelInvestmentChoicePasswordValidationPrecheckPolicy::toPrecheckViolation)
            .toList());
  }

  private static PasswordValidationPrecheckViolation toPrecheckViolation(
      InvestmentChoiceViolation violation) {
    PasswordValidationPrecheckViolation.Kind kind =
        switch (violation.kind()) {
          case FIELD -> PasswordValidationPrecheckViolation.Kind.FIELD;
          case CROSS_FIELD -> PasswordValidationPrecheckViolation.Kind.CROSS_FIELD;
          case FORM -> PasswordValidationPrecheckViolation.Kind.FORM;
        };
    return new PasswordValidationPrecheckViolation(kind, violation.code(), violation.targets());
  }

  @Override
  public String toString() {
    return "CancelInvestmentChoicePasswordValidationPrecheckPolicy";
  }
}
