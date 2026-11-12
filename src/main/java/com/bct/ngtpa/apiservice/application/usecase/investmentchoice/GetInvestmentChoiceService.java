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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class GetInvestmentChoiceService implements GetInvestmentChoiceUseCase {

  private static final String RETRIEVAL_PUBLIC_CODE = "err.investment-choice.retrieval.unavailable";
  private static final String IDENTIFIER_INVALID = "ACCOUNT_IDENTIFIER_INVALID";
  private static final String METADATA_INTEGRITY_EVENT =
      "investment-choice.retrieval.metadata_integrity";
  private static final String ALLOCATION_METADATA_MISSING = "ALLOCATION_METADATA_MISSING";

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
                .onErrorMap(error -> InvestmentChoiceDetailsRetrievalException.systemFailure());
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

    int missingSources = 0;
    int missingFunds = 0;
    for (InvestmentChoiceMandate mandate : details.mandates()) {
      if (sourceDescriptions.get(mandate.switchSource()) == null) {
        missingSources++;
      }
      if (!fundCodes.contains(mandate.fundCode())
          || fundsWithoutName.contains(mandate.fundCode())) {
        missingFunds++;
      }
    }
    if (details instanceof InvestmentChoiceDetails.Normal normal) {
      for (InvestmentChoicePending pending : normal.pending()) {
        for (InvestmentChoicePendingDetail allocation : pending.details()) {
          if (sourceDescriptions.get(allocation.switchSource()) == null) {
            missingSources++;
          }
          if (!fundCodes.contains(allocation.fundCode())
              || fundsWithoutName.contains(allocation.fundCode())) {
            missingFunds++;
          }
        }
      }
    }
    return new MetadataIntegrityCounts(missingSources, missingFunds);
  }

  private record MetadataIntegrityCounts(int missingSourceReferences, int missingFundReferences) {}
}
