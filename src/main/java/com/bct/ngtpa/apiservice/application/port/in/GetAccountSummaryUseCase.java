package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * D-4 of docs/refactor/3-account-endpoints-restructure/8-investgl-curr-required-addendum.md: {@code
 * sessionId}, {@code accountRef}, and {@code requestId} were added so the implementation can
 * read/write the session-cached available-currency list (D-2), mirroring {@code
 * FundComparisonOptionsUseCase.getOptionsAndCache}'s exact parameter shape.
 */
@FunctionalInterface
public interface GetAccountSummaryUseCase {
  Mono<AccountSummaryResult> get(
      GetAccountSummaryCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId);
}
