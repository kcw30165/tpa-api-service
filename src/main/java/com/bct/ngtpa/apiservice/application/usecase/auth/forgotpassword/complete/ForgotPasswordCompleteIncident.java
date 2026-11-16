package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import java.util.Objects;

/** Fixed, value-free incident metadata carried to the COMPLETE terminal publisher. */
public record ForgotPasswordCompleteIncident(
    FailureOrigin origin, String internalCode, String stage) {
  public ForgotPasswordCompleteIncident {
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(internalCode, "internalCode must not be null");
    Objects.requireNonNull(stage, "stage must not be null");
  }

  public static ForgotPasswordCompleteIncident apim(FailureOrigin origin, String stage) {
    return new ForgotPasswordCompleteIncident(origin, "COMPLETE_APIM_FAILURE", stage);
  }

  public static ForgotPasswordCompleteIncident local(String internalCode, String stage) {
    return new ForgotPasswordCompleteIncident(FailureOrigin.LOCAL_SYSTEM, internalCode, stage);
  }
}
