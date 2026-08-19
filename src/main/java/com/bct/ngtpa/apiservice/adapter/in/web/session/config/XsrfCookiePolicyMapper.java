package com.bct.ngtpa.apiservice.adapter.in.web.session.config;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.XsrfTokenTransportPolicy;
import java.util.Locale;
import java.util.Objects;

public class XsrfCookiePolicyMapper {

  public XsrfTokenTransportPolicy map(AuthFlowSessionProperties.Csrf csrfProperties) {
    Objects.requireNonNull(csrfProperties, "csrfProperties must not be null");
    var cookiePolicy =
        new SessionCookiePolicy(
            csrfProperties.getCookieName(),
            csrfProperties.getCookiePath(),
            csrfProperties.getMaxAgeSeconds(),
            false,
            csrfProperties.isSecure(),
            mapSameSite(csrfProperties.getSameSite()));
    return new XsrfTokenTransportPolicy(cookiePolicy, csrfProperties.getHeaderName());
  }

  private SessionCookieSameSite mapSameSite(String sameSite) {
    Objects.requireNonNull(sameSite, "sameSite must not be null");
    return switch (sameSite.trim().toUpperCase(Locale.ROOT)) {
      case "NONE" -> SessionCookieSameSite.NONE;
      case "LAX" -> SessionCookieSameSite.LAX;
      case "STRICT" -> SessionCookieSameSite.STRICT;
      default -> throw new IllegalArgumentException("Unsupported XSRF cookie SameSite policy");
    };
  }
}
