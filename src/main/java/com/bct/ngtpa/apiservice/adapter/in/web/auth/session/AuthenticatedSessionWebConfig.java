package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticatedSessionWebConfig {
  @Bean
  AuthenticatedSessionCookieWriter authenticatedSessionCookieWriter(
      AuthWorkflowProperties properties) {
    if (properties == null || properties.getLogin() == null) {
      throw new IllegalStateException("auth-workflows.login configuration is required");
    }
    var session = properties.getLogin().getSession();
    AuthenticatedSessionCookieLifetimePolicy.requireWithinAbsoluteTimeout(
        session.getCookie().getMaxAgeSeconds(), session.getAbsoluteTimeoutSeconds());
    return new AuthenticatedSessionCookieWriter(session.getCookie());
  }
}
