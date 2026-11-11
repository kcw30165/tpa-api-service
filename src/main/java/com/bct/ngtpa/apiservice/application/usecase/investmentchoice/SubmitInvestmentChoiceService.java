package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRow;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.SubmitInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionRejectedException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.application.port.in.SubmitInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceInstructionPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class SubmitInvestmentChoiceService implements SubmitInvestmentChoiceUseCase {

  private static final String SUBMISSION_UNAVAILABLE =
      "err.investment-choice.submission.unavailable";
  private static final ZoneId HONG_KONG_ZONE = ZoneId.of("Asia/Hong_Kong");
  private static final Logger LOG = LoggerFactory.getLogger(SubmitInvestmentChoiceService.class);

  private final ReferenceDatePort referenceDatePort;
  private final InvestmentChoiceDetailsPort detailsPort;
  private final InvestmentChoiceInstructionPort instructionPort;
  private final InvestmentChoiceSnapshotCacheService snapshotCache;
  private final Clock clock;

  public SubmitInvestmentChoiceService(
      ReferenceDatePort referenceDatePort,
      InvestmentChoiceDetailsPort detailsPort,
      InvestmentChoiceInstructionPort instructionPort,
      InvestmentChoiceSnapshotCacheService snapshotCache,
      Clock clock) {
    this.referenceDatePort = Objects.requireNonNull(referenceDatePort);
    this.detailsPort = Objects.requireNonNull(detailsPort);
    this.instructionPort = Objects.requireNonNull(instructionPort);
    this.snapshotCache = Objects.requireNonNull(snapshotCache);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<InvestmentChoiceSubmitOutcome> submit(SubmitInvestmentChoiceCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    List<InvestmentChoiceInstructionRow> rows = expand(command);
    InvestmentChoiceInstructionCommand instructionCommand =
        new InvestmentChoiceInstructionCommand(
            command.actorUserId(),
            command.actorUserRole(),
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            rows);

    return Mono.defer(() -> instructionPort.submit(instructionCommand))
        .<InvestmentChoiceSubmitOutcome>map(InvestmentChoiceSubmitOutcome.Submitted::new)
        .onErrorResume(
            failure ->
                failure instanceof InvestmentChoiceSubmissionRejectedException
                    ? Mono.error(unavailable())
                    : reconcile(command, rows))
        .switchIfEmpty(Mono.defer(() -> reconcile(command, rows)));
  }

  private List<InvestmentChoiceInstructionRow> expand(SubmitInvestmentChoiceCommand command) {
    List<InvestmentChoiceInstructionRow> rows = new ArrayList<>();
    for (String source : command.request().switchSources()) {
      for (var allocation : command.request().allocations()) {
        rows.add(
            new InvestmentChoiceInstructionRow(
                source, allocation.fundCode(), allocation.percentage()));
      }
    }
    return List.copyOf(rows);
  }

  private Mono<InvestmentChoiceSubmitOutcome> reconcile(
      SubmitInvestmentChoiceCommand command, List<InvestmentChoiceInstructionRow> rows) {
    return Mono.defer(() -> referenceDatePort.resolveReferenceDate(command.accountEnv()))
        .switchIfEmpty(Mono.error(unavailable()))
        .onErrorMap(error -> unavailable())
        .flatMap(
            referenceDate ->
                Mono.defer(
                        () ->
                            detailsPort.load(
                                new InvestmentChoiceDetailsCommand(
                                    command.actorUserRole(),
                                    command.accountEnv(),
                                    command.policyNo(),
                                    command.certNo(),
                                    referenceDate)))
                    .switchIfEmpty(Mono.error(unavailable()))
                    .onErrorMap(error -> unavailable())
                    .flatMap(details -> reconcileDetails(command, rows, details)));
  }

  private Mono<InvestmentChoiceSubmitOutcome> reconcileDetails(
      SubmitInvestmentChoiceCommand command,
      List<InvestmentChoiceInstructionRow> rows,
      InvestmentChoiceDetails details) {
    if (!(details instanceof InvestmentChoiceDetails.Normal normal)) {
      warn("investment-choice.submission.unreconciled", command.requestId());
      return Mono.defer(
              () ->
                  snapshotCache.writeRestrictedState(
                      command.sessionId(),
                      InvestmentChoiceSnapshotState.fromDetails(
                          command.snapshotAccountRef(), command.policyNo(), details),
                      command.requestId()))
          .onErrorResume(error -> Mono.empty())
          .then(Mono.error(unavailable()));
    }
    LocalDate today = LocalDate.now(clock.withZone(HONG_KONG_ZONE));
    Set<InstructionTuple> submittedRows = instructionTuples(rows);
    List<InvestmentChoicePending> candidates =
        normal.pending().stream()
            .filter(pending -> Objects.equals(pending.submitDate(), today))
            .filter(pending -> matchesRows(pending, rows.size(), submittedRows))
            .toList();
    if (candidates.size() != 1) {
      warn("investment-choice.submission.unreconciled", command.requestId());
      return Mono.error(unavailable());
    }

    InvestmentChoicePending candidate = candidates.getFirst();
    var submission =
        new InvestmentChoiceSubmission(
            candidate.refNo(), candidate.submitDate(), candidate.submitTime());
    warn("investment-choice.submission.reconciled", command.requestId());
    return Mono.defer(
            () ->
                snapshotCache.writeState(
                    command.sessionId(),
                    InvestmentChoiceSnapshotState.normal(
                        InvestmentChoiceSnapshot.fromDetails(
                            command.snapshotAccountRef(), command.policyNo(), normal)),
                    command.requestId()))
        .onErrorResume(error -> Mono.empty())
        .thenReturn(new InvestmentChoiceSubmitOutcome.Submitted(submission));
  }

  private boolean matchesRows(
      InvestmentChoicePending pending, int submittedSize, Set<InstructionTuple> submittedRows) {
    if (pending.details().size() != submittedSize) {
      return false;
    }
    Set<InstructionTuple> pendingRows = new HashSet<>();
    pending.details().stream()
        .map(
            detail ->
                new InstructionTuple(detail.switchSource(), detail.fundCode(), detail.percentage()))
        .forEach(pendingRows::add);
    return pendingRows.size() == submittedSize && pendingRows.equals(submittedRows);
  }

  private Set<InstructionTuple> instructionTuples(List<InvestmentChoiceInstructionRow> rows) {
    Set<InstructionTuple> tuples = new HashSet<>();
    rows.stream()
        .map(row -> new InstructionTuple(row.switchSource(), row.fundCode(), row.percentage()))
        .forEach(tuples::add);
    return Set.copyOf(tuples);
  }

  private void warn(String event, String requestId) {
    if (requestId == null) {
      LOG.warn("event={}", event);
      return;
    }
    LOG.warn("event={} requestId={}", event, requestId);
  }

  private InvestmentChoiceSubmissionUnavailableException unavailable() {
    return new InvestmentChoiceSubmissionUnavailableException();
  }

  private record InstructionTuple(String switchSource, String fundCode, int percentage) {}
}
