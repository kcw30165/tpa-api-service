package com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.AuthenticatedLogoutWebMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticatedLogoutWebConfig {

  @Bean
  AuthenticatedLogoutWebMapper authenticatedLogoutWebMapper() {
    return new AuthenticatedLogoutWebMapper();
  }
}
