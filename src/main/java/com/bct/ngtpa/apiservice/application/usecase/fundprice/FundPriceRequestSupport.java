package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Shared helper for {@link FundPriceQueryUseCase} and {@link FundPriceExportUseCase}: loads the
 * session and confirms it is ACTIVE with usable {@link FundPriceModeSessionMetadata}, and (for
 * query only) validates a candidate date against the cached range and available-date membership.
 *
 * <p>Package-private collaborator class (composition), not a shared abstract base class -- this
 * codebase does not use inheritance for use cases anywhere confirmed in this session; small focused
 * collaborator classes ({@code AuthenticatedAccountContextResolver}'s style) are the established
 * pattern.
 *
 * <p>{@code loadUsableSession(...)} returns {@code Optional<FundPriceModeSessionMetadata>}
 * directly: present means usable, empty means not usable. This deliberately does NOT wrap the
 * result in a custom type -- {@link
 * com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult}
 * and {@code FundPriceModeSessionMetadata} already carry everything the caller needs; a bespoke
 * wrapper type here would be redundant (simplification, script 113 -- an earlier version introduced
 * 3 such types and they added no value over a plain Optional).
 */
final class FundPriceRequestSupport {

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle;

  FundPriceRequestSupport(LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
  }

  Mono<Optional<FundPriceModeSessionMetadata>> loadUsableSession(AuthenticatedSessionId sessionId) {
    return lifecycle
        .loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(sessionId, null))
        .map(
            result -> {
              // A null session paired with ACTIVE status is impossible:
              // AuthenticatedSessionLifecycleResult's own compact constructor throws if
              // status==ACTIVE and session==null (confirmed, script 102's dump). Once
              // status != ACTIVE is ruled out via short-circuit OR, result.session() is
              // guaranteed non-null -- an explicit null check here would be unreachable
              // dead code.
              if (result.status() != AuthenticatedSessionLifecycleStatus.ACTIVE
                  || result.session().fundPriceModeMetadata() == null) {
                return Optional.<FundPriceModeSessionMetadata>empty();
              }
              return Optional.of(result.session().fundPriceModeMetadata());
            });
  }

  enum DateCheckOutcome {
    VALID,
    INVALID
  }

  /**
   * Validates {@code candidate} against {@code R.minusMonths(M) <= candidate <= R} and exact
   * membership in {@code metadata.availableFundPriceDates()}.
   */
  DateCheckOutcome validateDate(
      FundPriceModeSessionMetadata metadata, LocalDate referenceDate, LocalDate candidate) {
    LocalDate lowerBound = referenceDate.minusMonths(metadata.maximumFundPriceHistoryMonths());
    if (candidate.isBefore(lowerBound) || candidate.isAfter(referenceDate)) {
      return DateCheckOutcome.INVALID;
    }
    if (!metadata.availableFundPriceDates().contains(candidate)) {
      return DateCheckOutcome.INVALID;
    }
    return DateCheckOutcome.VALID;
  }
}
