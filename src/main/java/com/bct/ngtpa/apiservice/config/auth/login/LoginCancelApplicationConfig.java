package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel.CancelLoginWebMapper;
import com.bct.ngtpa.apiservice.application.port.in.CancelLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.CancelLoginService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class LoginCancelApplicationConfig {
  @Bean
  CancelLoginWebMapper cancelLoginWebMapper() {
    return new CancelLoginWebMapper();
  }

  @Bean
  CancelLoginUseCase cancelLoginUseCase(
      AuthFlowSessionPort s,
      AuthFlowSessionCsrfService c,
      OtpChallengePort o,
      AuthFlowSessionCancellationPort x,
      Clock clock) {
    return new CancelLoginService(s, c, o, x, clock);
  }
}
