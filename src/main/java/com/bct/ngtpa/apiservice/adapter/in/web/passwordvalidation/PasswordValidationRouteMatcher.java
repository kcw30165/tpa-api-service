package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class PasswordValidationRouteMatcher {
  public static final String ISSUANCE_PATH = "/api/v1/password-validation";
  public static final String PERSONAL_INFORMATION_PATH = "/api/v1/personal-information";
  private static final PathPattern ISSUANCE =
      PathPatternParser.defaultInstance.parse(ISSUANCE_PATH);
  private static final PathPattern PERSONAL_INFORMATION =
      PathPatternParser.defaultInstance.parse(PERSONAL_INFORMATION_PATH);

  private PasswordValidationRouteMatcher() {}

  public static boolean matches(ServerHttpRequest request) {
    var path = request.getPath().pathWithinApplication();
    return (HttpMethod.POST.equals(request.getMethod()) && ISSUANCE.matches(path))
        || (HttpMethod.PUT.equals(request.getMethod()) && PERSONAL_INFORMATION.matches(path));
  }
}
