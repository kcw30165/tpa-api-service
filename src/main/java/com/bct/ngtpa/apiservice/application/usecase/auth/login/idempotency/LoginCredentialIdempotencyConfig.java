package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;import com.bct.ngtpa.apiservice.application.port.out.LoginCredentialIdempotencyPort;import java.time.*;import org.springframework.context.annotation.*;
@Configuration public class LoginCredentialIdempotencyConfig {
 @Bean LoginCredentialRequestHasher loginCredentialRequestHasher(){return new LoginCredentialRequestHasher();}
 @Bean LoginCredentialIdempotencyCoordinator loginCredentialIdempotencyCoordinator(LoginCredentialIdempotencyPort port,Clock clock,AuthWorkflowProperties properties){properties.validateLoginPolicy();return new LoginCredentialIdempotencyCoordinator(port,clock,Duration.ofSeconds(properties.getLogin().getIdempotency().getStartTtlSeconds()));}
}
