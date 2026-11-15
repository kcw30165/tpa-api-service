package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class GetInvestmentChoiceService implements GetInvestmentChoiceUseCase {

  private static final String RETRIEVAL_PUBLIC_CODE = "err.investment-choice.retrieval.unavailable";
  private static final String IDENTIFIER_INVALID = "ACCOUNT_IDENTIFIER_INVALID";
  private static final String REFERENCE_DATE_FAILED =
      "investment-choice.retrieval.reference_date_failed";
  private static final String SNAPSHOT_WRITE_FAILED =
      "investment-choice.retrieval.snapshot_write_failed";
  private static final String REFERENCE_DATE_FAILURE_REASON = "REFERENCE_DATE_RESOLUTION_FAILED";
  private static final String RESTRICTED_SNAPSHOT_WRITE_FAILURE_REASON =
      "RESTRICTED_SNAPSHOT_WRITE_FAILED";
  private static final String METADATA_INTEGRITY_EVENT =
      "investment-choice.retrieval.metadata_integrity";
  private static final String ALLOCATION_METADATA_MISSING = "ALLOCATION_METADATA_MISSING";
  private static final String CAPABILITY = "INVESTMENT_CHOICE";

  private final ReferenceDatePort referenceDatePort;
  private final InvestmentChoiceDetailsPort detailsPort;
  private final AccountIdentifierFormatter accountIdentifierFormatter;
  private final InvestmentChoiceSnapshotCacheService snapshotCache;
  private final FailureEventPort failures;

  public GetInvestmentChoiceService(
      ReferenceDatePort referenceDatePort,
      InvestmentChoiceDetailsPort detailsPort,
      AccountIdentifierFormatter accountIdentifierFormatter,
      InvestmentChoiceSnapshotCacheService snapshotCache,
      FailureEventPort failures) {
    this.referenceDatePort = Objects.requireNonNull(referenceDatePort);
    this.detailsPort = Objects.requireNonNull(detailsPort);
    this.accountIdentifierFormatter = Objects.requireNonNull(accountIdentifierFormatter);
    this.snapshotCache = Objects.requireNonNull(snapshotCache);
    this.failures = Objects.requireNonNull(failures);
  }

  @Override
  public Mono<InvestmentChoiceResult> get(GetInvestmentChoiceCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .doOnError(error -> publishReferenceDateFailure(command))
        .onErrorMap(
            error -> !(error instanceof InvestmentChoiceDetailsRetrievalException),
            error -> InvestmentChoiceDetailsRetrievalException.systemFailure())
        .flatMap(
            referenceDate ->
                detailsPort
                    .load(
                        new InvestmentChoiceDetailsCommand(
                            command.actorUserRole(),
                            command.accountEnv(),
                            command.policyNo(),
                            command.certNo(),
                            referenceDate))
                    .flatMap(details -> assemble(command, details)));
  }

  private Mono<InvestmentChoiceResult> assemble(
      GetInvestmentChoiceCommand command, InvestmentChoiceDetails details) {
    String accountNumber;
    try {
      accountNumber = accountIdentifierFormatter.format(command.policyNo(), command.certNo());
    } catch (IllegalArgumentException exception) {
      publishIdentifierFailure(command);
      return Mono.error(InvestmentChoiceDetailsRetrievalException.systemFailure());
    }

    InvestmentChoiceSnapshotState state =
        InvestmentChoiceSnapshotState.fromDetails(
            command.snapshotAccountRef(), command.policyNo(), details);
    Mono<Void> write =
        state.scenario() == InvestmentChoiceScenario.NORMAL
            ? snapshotCache
                .writeState(command.sessionId(), state, command.requestId())
                .onErrorResume(error -> Mono.empty())
            : snapshotCache
                .writeRestrictedState(command.sessionId(), state, command.requestId())
                .onErrorMap(error -> InvestmentChoiceDetailsRetrievalException.systemFailure())
                .doOnError(error -> publishRestrictedSnapshotWriteFailure(command));
    return write.then(
        Mono.fromSupplier(
            () -> {
              publishMetadataIntegrityFailure(command, details);
              return new InvestmentChoiceResult(accountNumber, details);
            }));
  }

  private void publishIdentifierFailure(GetInvestmentChoiceCommand command) {
    try {
      failures.publish(
          new FailureEvent(
              "investment-choice.retrieval.local_failure",
              command.requestId(),
              "INVESTMENT_CHOICE",
              "GetInvestmentChoice",
              FailureOrigin.LOCAL_SYSTEM,
              IDENTIFIER_INVALID,
              RETRIEVAL_PUBLIC_CODE,
              503,
              true,
              Map.of("reason", IDENTIFIER_INVALID)));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the retrieval outcome.
    }
  }

  private void publishReferenceDateFailure(GetInvestmentChoiceCommand command) {
    publishSafely(
        new FailureEvent(
            REFERENCE_DATE_FAILED,
            command.requestId(),
            CAPABILITY,
            "REFERENCE_DATE",
            FailureOrigin.LOCAL_SYSTEM,
            null,
            RETRIEVAL_PUBLIC_CODE,
            503,
            true,
            Map.of("reason", REFERENCE_DATE_FAILURE_REASON)));
  }

  private void publishRestrictedSnapshotWriteFailure(GetInvestmentChoiceCommand command) {
    publishSafely(
        new FailureEvent(
            SNAPSHOT_WRITE_FAILED,
            command.requestId(),
            CAPABILITY,
            "SNAPSHOT",
            FailureOrigin.LOCAL_SYSTEM,
            null,
            RETRIEVAL_PUBLIC_CODE,
            503,
            true,
            Map.of("reason", RESTRICTED_SNAPSHOT_WRITE_FAILURE_REASON)));
  }

  private void publishSafely(FailureEvent event) {
    try {
      failures.publish(event);
    } catch (RuntimeException ignored) {
      // Telemetry must not change the retrieval outcome.
    }
  }

  private void publishMetadataIntegrityFailure(
      GetInvestmentChoiceCommand command, InvestmentChoiceDetails details) {
    MetadataIntegrityCounts counts = metadataIntegrityCounts(details);
    if (counts.missingSourceReferences() == 0 && counts.missingFundReferences() == 0) {
      return;
    }
    try {
      failures.publish(
          new FailureEvent(
              METADATA_INTEGRITY_EVENT,
              command.requestId(),
              "INVESTMENT_CHOICE",
              "GetInvestmentChoice",
              FailureOrigin.DOWNSTREAM_INVALID_RESPONSE,
              ALLOCATION_METADATA_MISSING,
              null,
              200,
              false,
              Map.of(
                  "reason",
                  ALLOCATION_METADATA_MISSING,
                  "missingSourceReferences",
                  counts.missingSourceReferences(),
                  "missingFundReferences",
                  counts.missingFundReferences())));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the successful retrieval outcome.
    }
  }

  private static MetadataIntegrityCounts metadataIntegrityCounts(InvestmentChoiceDetails details) {
    MetadataReferenceIndex metadata = metadataReferenceIndex(details);
    MetadataIntegrityCounts counts = countMandates(details.mandates(), metadata);
    if (details instanceof InvestmentChoiceDetails.Normal normal) {
      counts = counts.plus(countPending(normal.pending(), metadata));
    }
    return counts;
  }

  private static MetadataReferenceIndex metadataReferenceIndex(InvestmentChoiceDetails details) {
    Map<String, String> sourceDescriptions = new HashMap<>();
    for (InvestmentChoiceSource source : details.historySources()) {
      sourceDescriptions.putIfAbsent(source.code(), source.description());
    }
    Set<String> fundCodes = new HashSet<>();
    Set<String> fundsWithoutName = new HashSet<>();
    for (InvestmentChoiceFund fund : details.historyFunds()) {
      fundCodes.add(fund.code());
      if (fund.name() == null) {
        fundsWithoutName.add(fund.code());
      }
    }
    return new MetadataReferenceIndex(sourceDescriptions, fundCodes, fundsWithoutName);
  }

  private static MetadataIntegrityCounts countMandates(
      List<InvestmentChoiceMandate> mandates, MetadataReferenceIndex metadata) {
    int missingSources = 0;
    int missingFunds = 0;
    for (InvestmentChoiceMandate mandate : mandates) {
      missingSources += missingSourceReference(mandate.switchSource(), metadata);
      missingFunds += missingFundReference(mandate.fundCode(), metadata);
    }
    return new MetadataIntegrityCounts(missingSources, missingFunds);
  }

  private static MetadataIntegrityCounts countPending(
      List<InvestmentChoicePending> pendingInstructions, MetadataReferenceIndex metadata) {
    int missingSources = 0;
    int missingFunds = 0;
    for (InvestmentChoicePending pending : pendingInstructions) {
      for (InvestmentChoicePendingDetail allocation : pending.details()) {
        missingSources += missingSourceReference(allocation.switchSource(), metadata);
        missingFunds += missingFundReference(allocation.fundCode(), metadata);
      }
    }
    return new MetadataIntegrityCounts(missingSources, missingFunds);
  }

  private static int missingSourceReference(String sourceCode, MetadataReferenceIndex metadata) {
    return metadata.hasSourceDescription(sourceCode) ? 0 : 1;
  }

  private static int missingFundReference(String fundCode, MetadataReferenceIndex metadata) {
    return metadata.hasFundName(fundCode) ? 0 : 1;
  }

  private record MetadataReferenceIndex(
      Map<String, String> sourceDescriptions, Set<String> fundCodes, Set<String> fundsWithoutName) {
    private boolean hasSourceDescription(String sourceCode) {
      return sourceDescriptions.get(sourceCode) != null;
    }

    private boolean hasFundName(String fundCode) {
      return fundCodes.contains(fundCode) && !fundsWithoutName.contains(fundCode);
    }
  }

  private record MetadataIntegrityCounts(int missingSourceReferences, int missingFundReferences) {
    private MetadataIntegrityCounts plus(MetadataIntegrityCounts other) {
      return new MetadataIntegrityCounts(
          missingSourceReferences + other.missingSourceReferences,
          missingFundReferences + other.missingFundReferences);
    }
  }
}
