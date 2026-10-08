package com.bct.ngtpa.apiservice.application.usecase.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FetchFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetFundFactSheetUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Resolves the reference date, fetches the raw rows and normalizes them. The whole chain runs
 * inside one deferred pipeline, and every failure that is not already a {@link
 * FundFactSheetRetrievalException} becomes one. A reference date or a source that completes without
 * a value also fails, so a missing signal can never turn into an empty successful response.
 *
 * <p>Deliberately not annotated with a Spring stereotype: it is wired from {@code
 * FundFactSheetApplicationConfig}.
 */
public final class GetFundFactSheetService implements GetFundFactSheetUseCase {

  private final ReferenceDatePort referenceDatePort;
  private final FundFactSheetSourcePort source;
  private final FundFactSheetNormalizer normalizer;

  public GetFundFactSheetService(
      ReferenceDatePort referenceDatePort,
      FundFactSheetSourcePort source,
      FundFactSheetNormalizer normalizer) {
    this.referenceDatePort =
        Objects.requireNonNull(referenceDatePort, "referenceDatePort must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
  }

  @Override
  public Mono<FundFactSheetResult> get(GetFundFactSheetCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () ->
                referenceDatePort
                    .resolveReferenceDate(command.accountEnv())
                    .flatMap(
                        date ->
                            source.fetch(
                                new FetchFundFactSheetCommand(
                                    command.policyNo(),
                                    date,
                                    command.accountEnv(),
                                    command.actorUserRole())))
                    .map(normalizer::normalize)
                    .switchIfEmpty(Mono.error(FundFactSheetRetrievalException::systemFailure)))
        .onErrorMap(
            ex -> !(ex instanceof FundFactSheetRetrievalException),
            ex -> FundFactSheetRetrievalException.systemFailure());
  }
}
