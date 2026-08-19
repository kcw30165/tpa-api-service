package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import java.util.Objects;

public record ResolvedLoginOtpDestination(
    String destinationId, OtpChannel channel, String destination, String maskedDestination) {
  public ResolvedLoginOtpDestination {
    Objects.requireNonNull(destinationId);
    Objects.requireNonNull(channel);
    Objects.requireNonNull(destination);
    Objects.requireNonNull(maskedDestination);
  }

  @Override
  public String toString() {
    return "ResolvedLoginOtpDestination[destinationId=<redacted>, channel="
        + channel
        + ", destination=<redacted>, maskedDestination=<redacted>]";
  }
}
