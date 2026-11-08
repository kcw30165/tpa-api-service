package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class PasswordValidationRouteMatcher {
  // Fixed public route contracts, not configurable downstream addresses.
  @SuppressWarnings("java:S1075")
  public static final String ISSUANCE_PATH = "/api/v1/password-validation";

  @SuppressWarnings("java:S1075")
  public static final String PERSONAL_INFORMATION_PATH = "/api/v1/personal-information";

  @SuppressWarnings("java:S1075")
  public static final String INVESTMENT_CHOICE_INSTRUCTIONS_PATH =
      "/api/v1/investment-choice/instructions";

  @SuppressWarnings("java:S1075")
  public static final String INVESTMENT_CHOICE_CANCEL_PATH =
      "/api/v1/investment-choice/instructions/cancel";

  private static final PathPattern ISSUANCE =
      PathPatternParser.defaultInstance.parse(ISSUANCE_PATH);
  private static final PathPattern PERSONAL_INFORMATION =
      PathPatternParser.defaultInstance.parse(PERSONAL_INFORMATION_PATH);
  private static final PathPattern INVESTMENT_CHOICE_INSTRUCTIONS =
      PathPatternParser.defaultInstance.parse(INVESTMENT_CHOICE_INSTRUCTIONS_PATH);
  private static final PathPattern INVESTMENT_CHOICE_CANCEL =
      PathPatternParser.defaultInstance.parse(INVESTMENT_CHOICE_CANCEL_PATH);

  private PasswordValidationRouteMatcher() {}

  public static boolean matches(ServerHttpRequest request) {
    var path = request.getPath().pathWithinApplication();
    return (HttpMethod.POST.equals(request.getMethod()) && ISSUANCE.matches(path))
        || purposeForTokenGate(request).isPresent();
  }

  public static Optional<PasswordValidationPurpose> purposeForTokenGate(ServerHttpRequest request) {
    var path = request.getPath().pathWithinApplication();
    if (HttpMethod.PUT.equals(request.getMethod()) && PERSONAL_INFORMATION.matches(path)) {
      return Optional.of(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE);
    }
    if (HttpMethod.POST.equals(request.getMethod())
        && INVESTMENT_CHOICE_INSTRUCTIONS.matches(path)) {
      return Optional.of(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE);
    }
    if (HttpMethod.POST.equals(request.getMethod()) && INVESTMENT_CHOICE_CANCEL.matches(path)) {
      return Optional.of(PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE);
    }
    return Optional.empty();
  }
}
