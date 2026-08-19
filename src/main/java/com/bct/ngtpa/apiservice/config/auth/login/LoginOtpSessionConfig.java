package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.out.session.login.LoginOtpSessionBindingAdapter;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.context.DefaultLoginProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class LoginOtpSessionConfig {
  @Bean
  LoginProtectedAuthFlowContextLoader loginProtectedAuthFlowContextLoader(
      AuthFlowSessionLifecycleService l,
      AuthFlowSessionCsrfService c,
      LoginPreAuthContextProtectorPort p,
      Clock clock) {
    return new DefaultLoginProtectedAuthFlowContextLoader(l, c, p, clock);
  }

  @Bean
  LoginOtpSessionBindingPort loginOtpSessionBindingPort(AuthFlowSessionLifecycleService l) {
    return new LoginOtpSessionBindingAdapter(l);
  }
}
