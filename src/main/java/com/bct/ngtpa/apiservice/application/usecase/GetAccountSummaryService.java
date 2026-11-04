package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.FetchAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.GetAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryCurrencyCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolveGeneralMetadataQuery;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountSummaryRequestInvalidException;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import com.bct.ngtpa.apiservice.application.service.accountsummary.AccountSummaryAssembler;
import com.bct.ngtpa.apiservice.application.usecase.accountsummary.AccountSummaryCurrencyCacheService;
import java.time.LocalDate;
import java.util.Optional;
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
 *
 * <p>InvestGL curr-required-currency handling (D-1 through D-4 of
 * docs/refactor/3-account-endpoints-restructure/8-investgl-curr-required-addendum.md): when {@code
 * command.currency()} is blank, the gain/loss call uses the balance call's own {@code
 * selectedCurrency} instead of the blank value, and the balance call's available-currency list is
 * cached in the authenticated session after it succeeds (D-2). When {@code command.currency()} is
 * supplied, it is validated against that session-cached list before either downstream operation is
 * called; a cache miss for any reason (including a missing session cookie) is rejected the same
 * way as a non-member currency (D-2/D-3). The balance call itself always receives {@code
 * command.currency()} verbatim, blank or not.
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
  private final AccountSummaryCurrencyCacheService currencyCacheService;

  public GetAccountSummaryService(
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver,
      AccountBalanceSourcePort accountBalanceSourcePort,
      AccountGainLossSourcePort accountGainLossSourcePort,
      AccountBalanceAssembler accountBalanceAssembler,
      AccountGainLossAssembler accountGainLossAssembler,
      AccountSummaryAssembler accountSummaryAssembler,
      AccountSummaryCurrencyCacheService currencyCacheService) {
    this.referenceDatePort = referenceDatePort;
    this.generalMetadataResolver = generalMetadataResolver;
    this.accountBalanceSourcePort = accountBalanceSourcePort;
    this.accountGainLossSourcePort = accountGainLossSourcePort;
    this.accountBalanceAssembler = accountBalanceAssembler;
    this.accountGainLossAssembler = accountGainLossAssembler;
    this.accountSummaryAssembler = accountSummaryAssembler;
    this.currencyCacheService = currencyCacheService;
  }

  @Override
  public Mono<AccountSummaryResult> get(
      GetAccountSummaryCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    if (command.currency() != null) {
      return validateSuppliedCurrency(command, sessionId, accountRef, requestId)
          .then(Mono.defer(() -> proceed(command, sessionId, accountRef, requestId, false)));
    }
    return proceed(command, sessionId, accountRef, requestId, true);
  }

  private Mono<Void> validateSuppliedCurrency(
      GetAccountSummaryCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    if (sessionId.isEmpty()) {
      return Mono.error(
          new AccountSummaryRequestInvalidException("Missing authenticated session cookie"));
    }
    return currencyCacheService
        .read(sessionId.get(), accountRef, command.policyNo(), requestId)
        .flatMap(
            cached -> {
              if (cached.isEmpty()
                  || !cached.get().availableCurrencies().contains(command.currency())) {
                return Mono.<Void>error(
                    new AccountSummaryRequestInvalidException(
                        "currency is not an available currency for this account"));
              }
              return Mono.empty();
            });
  }

  private Mono<AccountSummaryResult> proceed(
      GetAccountSummaryCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId,
      boolean cacheOnSuccess) {
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
                  .flatMap(
                      metadata ->
                          fetchAndAssemble(
                              command,
                              referenceDate,
                              metadata,
                              sessionId,
                              accountRef,
                              requestId,
                              cacheOnSuccess));
            })
        .onErrorMap(
            ex ->
                !(ex instanceof AccountBalanceRetrievalException
                    || ex instanceof AccountGainLossRetrievalException
                    || ex instanceof AccountSummaryRequestInvalidException),
            ex -> AccountBalanceRetrievalException.systemFailure());
  }

  private Mono<AccountSummaryResult> fetchAndAssemble(
      GetAccountSummaryCommand command,
      LocalDate referenceDate,
      ResolvedGeneralMetadataSet metadata,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId,
      boolean cacheOnSuccess) {
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
                        balanceResult -> {
                          String effectiveCurrency =
                              command.currency() != null
                                  ? command.currency()
                                  : balanceResult.currencySelection().selectedCurrency();
                          Mono<Void> cacheWrite =
                              cacheOnSuccess
                                  ? writeCurrencyCache(
                                      command, balanceResult, sessionId, accountRef, requestId)
                                  : Mono.empty();
                          return cacheWrite.then(
                              Mono.defer(
                                  () ->
                                      fetchGainLossAndAssemble(
                                          command,
                                          referenceDate,
                                          metadata,
                                          rawBalance,
                                          balanceResult,
                                          effectiveCurrency)));
                        }));
  }

  private Mono<Void> writeCurrencyCache(
      GetAccountSummaryCommand command,
      AccountBalanceResult balanceResult,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    if (sessionId.isEmpty()) {
      return Mono.empty();
    }
    var entry =
        AccountSummaryCurrencyCacheEntry.of(
            accountRef, command.policyNo(), balanceResult.currencySelection());
    return currencyCacheService.write(sessionId.get(), entry, requestId);
  }

  private Mono<AccountSummaryResult> fetchGainLossAndAssemble(
      GetAccountSummaryCommand command,
      LocalDate referenceDate,
      ResolvedGeneralMetadataSet metadata,
      RawAccountBalanceData rawBalance,
      AccountBalanceResult balanceResult,
      String effectiveCurrency) {
    var gainLossCommand =
        new GetAccountGainLossCommand(
            command.language(),
            command.period(),
            effectiveCurrency,
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
            effectiveCurrency,
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
