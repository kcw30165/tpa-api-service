package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import org.springframework.context.annotation.*;

@Configuration
public class LoginWebConfig {
  @Bean
  LoginStartWebMapper loginStartWebMapper() {
    return new LoginStartWebMapper();
  }
}
