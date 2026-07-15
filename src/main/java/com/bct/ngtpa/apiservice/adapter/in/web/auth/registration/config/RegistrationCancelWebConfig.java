package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationCancelWebConfig {
    @Bean
    @ConditionalOnMissingBean
    RegistrationCancellationPageMapper registrationCancellationPageMapper(
            BffPagesProperties pages,
            PageDisplayTextResolver displayTextResolver) {
        return new RegistrationCancellationPageMapper(pages, displayTextResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    CancelRegistrationWebMapper cancelRegistrationWebMapper(
            RegistrationCancellationPageMapper pageMapper,
            ErrorMessageResolver messages) {
        return new CancelRegistrationWebMapper(pageMapper, messages);
    }

    @Bean
    @ConditionalOnMissingBean
    XsrfCookieWriter xsrfCookieWriter(AuthFlowSessionProperties properties) {
        return new XsrfCookieWriter(properties);
    }
}
