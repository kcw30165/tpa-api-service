package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import reactor.core.publisher.Mono;

public interface ForgotPasswordDetailsLookupPort {
  Mono<ForgotPasswordDetailsLookupResult> verify(ForgotPasswordDetailsLookupCommand command);

  /** Classifies a technical failure from {@link #verify} without repeating the lookup. */
  FailureOrigin classifyFailure(Throwable failure);

  /** Retains the legacy unavailable-response mapping independently of incident origin. */
  default boolean isUnavailableFailure(Throwable failure) {
    return false;
  }
}
