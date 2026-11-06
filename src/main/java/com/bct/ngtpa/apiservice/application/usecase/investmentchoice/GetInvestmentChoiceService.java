package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class GetInvestmentChoiceService implements GetInvestmentChoiceUseCase {

  private static final String RETRIEVAL_PUBLIC_CODE = "err.investment-choice.retrieval.unavailable";
  private static final String IDENTIFIER_INVALID = "ACCOUNT_IDENTIFIER_INVALID";
  private static final Logger LOG = LoggerFactory.getLogger(GetInvestmentChoiceService.class);

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

    InvestmentChoiceSnapshot snapshot =
        InvestmentChoiceSnapshot.fromDetails(
            command.snapshotAccountRef(), command.policyNo(), details);
    return snapshotCache
        .write(command.sessionId(), snapshot, command.requestId())
        .onErrorResume(
            error -> {
              LOG.warn(
                  "event=investment-choice.snapshot-cache.write.skipped reason=failed requestId={}",
                  command.requestId());
              return Mono.empty();
            })
        .thenReturn(new InvestmentChoiceResult(accountNumber, details));
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
}
