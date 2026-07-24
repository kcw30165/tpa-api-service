package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.out.session.login.LoginOtpReplacementBindingAdapter;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.context.DefaultLoginOtpResendContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class LoginOtpResendSessionConfig {
    @Bean
    LoginOtpResendContextLoader loginOtpResendContextLoader(AuthFlowSessionLifecycleService l,
            AuthFlowSessionCsrfService c, LoginPreAuthContextProtectorPort p, Clock clock) {
        return new DefaultLoginOtpResendContextLoader(l, c, p, clock);
    }

    @Bean
    LoginOtpReplacementBindingPort loginOtpReplacementBindingPort(AuthFlowSessionLifecycleService l) {
        return new LoginOtpReplacementBindingAdapter(l);
    }
}
