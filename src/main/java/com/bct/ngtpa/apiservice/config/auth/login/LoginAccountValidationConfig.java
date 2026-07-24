package com.bct.ngtpa.apiservice.config.auth.login;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.LoginAccountIdentifierProperties;import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountRecordValidationService;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.context.annotation.*;
@Configuration public class LoginAccountValidationConfig {
 @Bean @ConfigurationProperties("auth-workflows.login.account-identifiers") LoginAccountIdentifierProperties loginAccountIdentifierProperties(){return new LoginAccountIdentifierProperties();}
 @Bean LoginAccountRecordValidationService loginAccountRecordValidationService(LoginAccountIdentifierProperties p){p.validate();return new LoginAccountRecordValidationService(p.getPolicyNo().getWidth(),p.getCertNo().getWidth(),p.getPadCharacter().charAt(0));}
}
