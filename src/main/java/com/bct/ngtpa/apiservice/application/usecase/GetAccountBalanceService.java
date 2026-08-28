package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountBalanceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceDataPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetAccountBalanceService implements GetAccountBalanceUseCase {
  private final ReferenceDatePort referenceDatePort;
  private final AccountBalanceDataPort accountBalanceDataPort;

  @Override
  public Mono<AccountBalanceResult> get(GetAccountBalanceCommand command) {
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .flatMap(referenceDate -> accountBalanceDataPort.fetchAccountBalance(
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
                referenceDate)));
  }
}
