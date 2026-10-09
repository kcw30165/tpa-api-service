package com.bct.ngtpa.apiservice.application.usecase.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FetchFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetFundFactSheetUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetSourcePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Checks the account identifiers, fetches the raw rows and normalizes them. The whole chain runs
 * inside one deferred pipeline. If the policy number, the certificate number or the scheme type is
 * null or blank, the use case reports one value-free event and fails before the source port is
 * called, so the downstream service is never called. Every failure that is not already a {@link
 * FundFactSheetRetrievalException} becomes one, and a source that completes without a value also
 * fails, so a missing signal can never turn into an empty successful response.
 *
 * <p>Deliberately not annotated with a Spring stereotype: it is wired from {@code
 * FundFactSheetApplicationConfig}.
 */
public final class GetFundFactSheetService implements GetFundFactSheetUseCase {

  private final FundFactSheetSourcePort source;
  private final FundFactSheetNormalizer normalizer;
  private final FundFactSheetPresentationEventPort events;

  public GetFundFactSheetService(
      FundFactSheetSourcePort source,
      FundFactSheetNormalizer normalizer,
      FundFactSheetPresentationEventPort events) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
  }

  @Override
  public Mono<FundFactSheetResult> get(GetFundFactSheetCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              if (!hasText(command.policyNo())
                  || !hasText(command.certNo())
                  || !hasText(command.schemeType())) {
                events.missingAccountIdentifier();
                return Mono.<FundFactSheetResult>error(
                    FundFactSheetRetrievalException.systemFailure());
              }
              return source
                  .fetch(
                      new FetchFundFactSheetCommand(
                          command.policyNo(),
                          command.certNo(),
                          command.schemeType(),
                          command.accountEnv(),
                          command.actorUserRole()))
                  .map(normalizer::normalize)
                  .switchIfEmpty(Mono.error(FundFactSheetRetrievalException::systemFailure));
            })
        .onErrorMap(
            ex -> !(ex instanceof FundFactSheetRetrievalException),
            ex -> FundFactSheetRetrievalException.systemFailure());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
