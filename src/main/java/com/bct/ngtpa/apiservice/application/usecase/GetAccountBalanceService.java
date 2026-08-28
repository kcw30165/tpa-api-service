package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetAccountBalanceService implements GetAccountBalanceUseCase {
  private static final Set<GeneralMetadataKey> REQUIRED_METADATA =
      Set.of(
          key(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.ROUNDING_METHOD),
          key(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.ROUNDING_METHOD),
          key(GeneralMetadataName.UNIT_BALANCE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.UNIT_BALANCE, GeneralMetadataType.ROUNDING_METHOD),
          key(GeneralMetadataName.FX_RATE, GeneralMetadataType.PRECISION),
          key(GeneralMetadataName.FX_RATE, GeneralMetadataType.ROUNDING_METHOD));
  private final ReferenceDatePort referenceDatePort;
  private final GeneralMetadataResolver generalMetadataResolver;
  private final AccountBalanceSourcePort accountBalanceSourcePort;
  private final AccountBalanceAssembler accountBalanceAssembler;

  @Override
  public Mono<AccountBalanceResult> get(GetAccountBalanceCommand command) {
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
                      metadata -> {
                        var fetch =
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
                            .fetchRawAccountBalance(fetch)
                            .flatMap(
                                raw ->
                                    accountBalanceAssembler.assemble(
                                        command, referenceDate, metadata, raw));
                      });
            });
  }

  private static GeneralMetadataKey key(GeneralMetadataName name, GeneralMetadataType type) {
    return new GeneralMetadataKey(name, type);
  }
}
