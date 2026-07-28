package com.bct.ngtpa.apiservice.adapter.out.security.login.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.out.security.login.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(LoginPreAuthContextProtectionProperties.class)
public class LoginPreAuthFoundationConfig {
    @Bean
    LoginDestinationIdGenerator loginDestinationIdGenerator(LoginPreAuthContextProtectionProperties p) {
        return new SecureRandomLoginDestinationIdGenerator(new SecureRandom(), p.getDestinationIdEntropyBytes());
    }

    @Bean
    LoginPreAuthContextProtectorPort loginPreAuthContextProtector(ObjectMapper mapper,
            LoginPreAuthContextProtectionProperties p) {
        if (!p.isEnabled())
            throw new IllegalStateException("Login pre-auth context protection must be enabled");
        return new AesGcmLoginPreAuthContextProtector(mapper, p.getKeyVersion(), p.getSecret());
    }

    @Bean
    LoginDestinationMasker loginDestinationMasker() {
        return new LoginDestinationMasker();
    }

    @Bean
    LoginPreAuthContextFactory loginPreAuthContextFactory(LoginDestinationIdGenerator ids,
            LoginDestinationMasker masker) {
        return new LoginPreAuthContextFactory(ids, masker);
    }

    @Bean
    LoginCredentialPolicyService loginCredentialPolicyService(AuthWorkflowProperties props) {
        props.validateLoginPolicy();
        var c = props.getLogin().getCredential();
        return new LoginCredentialPolicyService(c.isUsernameTrim(),
                LoginUsernameNormalization.valueOf(c.getUsernameNormalization()), c.getUsernameMinLength(),
                c.getUsernameMaxLength(), c.getUsernameAllowedPattern(), c.isPasswordTrim(),
                c.isPasswordCaseSensitive(), c.getPasswordMinLength(), c.getPasswordMaxLength());
    }
}
