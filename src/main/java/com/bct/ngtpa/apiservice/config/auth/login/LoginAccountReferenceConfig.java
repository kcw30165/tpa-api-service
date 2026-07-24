package com.bct.ngtpa.apiservice.config.auth.login;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.LoginAccountReferenceProperties;import com.bct.ngtpa.apiservice.adapter.out.security.login.account.SecureRandomLoginAccountRefGenerator;import com.bct.ngtpa.apiservice.application.port.out.LoginAccountRefGeneratorPort;import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountReferenceAssignmentService;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.context.annotation.*;
@Configuration public class LoginAccountReferenceConfig {
 @Bean @ConfigurationProperties("auth-workflows.login.account-ref") LoginAccountReferenceProperties loginAccountReferenceProperties(){return new LoginAccountReferenceProperties();}
 @Bean LoginAccountRefGeneratorPort loginAccountRefGeneratorPort(LoginAccountReferenceProperties p){p.validate();return new SecureRandomLoginAccountRefGenerator(p.getPrefix(),p.getEntropyBytes());}
 @Bean LoginAccountReferenceAssignmentService loginAccountReferenceAssignmentService(LoginAccountRefGeneratorPort generator,LoginAccountReferenceProperties p){p.validate();return new LoginAccountReferenceAssignmentService(generator,p.getMaxGenerationAttempts());}
}
