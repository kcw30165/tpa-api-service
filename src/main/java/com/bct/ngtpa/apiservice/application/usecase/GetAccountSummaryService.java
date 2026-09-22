package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.FetchAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.GetAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolveGeneralMetadataQuery;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import com.bct.ngtpa.apiservice.application.service.accountsummary.AccountSummaryAssembler;
import java.time.LocalDate;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Resolves one reference date and one metadata set, then composes the Account Balance and Account
 * Gain/Loss application services sequentially - balance first, gain/loss only if balance succeeds
 * (Q-6: deterministic error precedence; the structural base of the payload is confirmed retrievable
 * before the second upstream call is made).
 *
 * <p>Neither existing assembler is modified. This class only adds the new summary-specific
 * composition performed by {@link AccountSummaryAssembler}.
 */
public final class GetAccountSummaryService implements GetAccountSummaryUseCase {

  private static final Set<GeneralMetadataKey> REQUIRED_METADATA =
      Set.of(
          key(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.UNIT_BALANCE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.FX_RATE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.AB_PERCENTAGE, GeneralMetadataType.PRECISION));

  private final ReferenceDatePort referenceDatePort;
  private final GeneralMetadataResolver generalMetadataResolver;
  private final AccountBalanceSourcePort accountBalanceSourcePort;
  private final AccountGainLossSourcePort accountGainLossSourcePort;
  private final AccountBalanceAssembler accountBalanceAssembler;
  private final AccountGainLossAssembler accountGainLossAssembler;
  private final AccountSummaryAssembler accountSummaryAssembler;

  public GetAccountSummaryService(
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver,
      AccountBalanceSourcePort accountBalanceSourcePort,
      AccountGainLossSourcePort accountGainLossSourcePort,
      AccountBalanceAssembler accountBalanceAssembler,
      AccountGainLossAssembler accountGainLossAssembler,
      AccountSummaryAssembler accountSummaryAssembler) {
    this.referenceDatePort = referenceDatePort;
    this.generalMetadataResolver = generalMetadataResolver;
    this.accountBalanceSourcePort = accountBalanceSourcePort;
    this.accountGainLossSourcePort = accountGainLossSourcePort;
    this.accountBalanceAssembler = accountBalanceAssembler;
    this.accountGainLossAssembler = accountGainLossAssembler;
    this.accountSummaryAssembler = accountSummaryAssembler;
  }

  @Override
  public Mono<AccountSummaryResult> get(GetAccountSummaryCommand command) {
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .flatMap(
            referenceDate -> {
              var query =
                  new ResolveGeneralMetadataQuery(
                      new GeneralMetadataScope(
                          command.accountEnv(), command.trustCode(), command.policyNo()),
                      REQUIRED_METADATA,
                      command.actorUserRole());
              return generalMetadataResolver
                  .resolve(query)
                  .flatMap(metadata -> fetchAndAssemble(command, referenceDate, metadata));
            })
        .onErrorMap(
            ex ->
                !(ex instanceof AccountBalanceRetrievalException
                    || ex instanceof AccountGainLossRetrievalException),
            ex -> AccountBalanceRetrievalException.systemFailure());
  }

  private Mono<AccountSummaryResult> fetchAndAssemble(
      GetAccountSummaryCommand command,
      LocalDate referenceDate,
      ResolvedGeneralMetadataSet metadata) {
    var balanceCommand =
        new GetAccountBalanceCommand(
            command.language(),
            command.currency(),
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.trustCode(),
            command.schemeType(),
            command.actorUserId(),
            command.actorUserRole());
    var fetchBalance =
        new FetchAccountBalanceCommand(
            command.language(),
            command.currency(),
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.trustCode(),
            command.schemeType(),
            command.actorUserId(),
            command.actorUserRole(),
            referenceDate);

    return accountBalanceSourcePort
        .fetchRawAccountBalance(fetchBalance)
        .flatMap(
            rawBalance ->
                accountBalanceAssembler
                    .assemble(balanceCommand, referenceDate, metadata, rawBalance)
                    .flatMap(
                        balanceResult ->
                            fetchGainLossAndAssemble(
                                command, referenceDate, metadata, rawBalance, balanceResult)));
  }

  private Mono<AccountSummaryResult> fetchGainLossAndAssemble(
      GetAccountSummaryCommand command,
      LocalDate referenceDate,
      ResolvedGeneralMetadataSet metadata,
      RawAccountBalanceData rawBalance,
      com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult balanceResult) {
    var gainLossCommand =
        new GetAccountGainLossCommand(
            command.language(),
            command.period(),
            command.currency(),
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.trustCode(),
            command.schemeType(),
            command.actorUserId(),
            command.actorUserRole());
    var fetchGainLoss =
        new FetchAccountGainLossCommand(
            command.period(),
            command.currency(),
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.actorUserId(),
            command.actorUserRole(),
            referenceDate);

    return accountGainLossSourcePort
        .fetch(fetchGainLoss)
        .map(
            rawGainLoss -> {
              var gainLossResult =
                  accountGainLossAssembler.assemble(gainLossCommand, metadata, rawGainLoss);
              return accountSummaryAssembler.assemble(
                  command, balanceResult, gainLossResult, rawBalance, metadata);
            });
  }

  private static GeneralMetadataKey key(GeneralMetadataName name, GeneralMetadataType type) {
    return new GeneralMetadataKey(name, type);
  }
}
