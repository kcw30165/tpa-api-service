package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.accountgainloss.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class GetAccountGainLossService implements GetAccountGainLossUseCase {
  private static final GeneralMetadataKey NAV_PRECISION =
      new GeneralMetadataKey(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION);
  private final ReferenceDatePort referenceDatePort;
  private final GeneralMetadataResolver metadataResolver;
  private final AccountGainLossSourcePort source;
  private final AccountGainLossAssembler assembler;

  public GetAccountGainLossService(
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver metadataResolver,
      AccountGainLossSourcePort source,
      AccountGainLossAssembler assembler) {
    this.referenceDatePort = referenceDatePort;
    this.metadataResolver = metadataResolver;
    this.source = source;
    this.assembler = assembler;
  }

  @Override
  public Mono<AccountGainLossResult> get(GetAccountGainLossCommand command) {
    return Mono.defer(
            () ->
                referenceDatePort
                    .resolveReferenceDate(command.accountEnv())
                    .flatMap(
                        date ->
                            metadataResolver
                                .resolve(
                                    new ResolveGeneralMetadataQuery(
                                        new GeneralMetadataScope(
                                            command.accountEnv(),
                                            command.trustCode(),
                                            command.policyNo()),
                                        Set.of(NAV_PRECISION),
                                        command.actorUserRole()))
                                .flatMap(
                                    metadata ->
                                        source
                                            .fetch(
                                                new FetchAccountGainLossCommand(
                                                    command.period(),
                                                    command.accountEnv(),
                                                    command.policyNo(),
                                                    command.certNo(),
                                                    command.actorUserId(),
                                                    command.actorUserRole(),
                                                    date))
                                            .map(
                                                raw ->
                                                    assembler.assemble(command, metadata, raw)))))
        .onErrorMap(
            ex -> !(ex instanceof AccountGainLossRetrievalException),
            ex -> AccountGainLossRetrievalException.systemFailure());
  }
}
