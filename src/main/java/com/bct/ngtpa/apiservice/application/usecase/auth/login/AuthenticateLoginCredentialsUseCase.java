package com.bct.ngtpa.apiservice.application.usecase.auth.login;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Application entry point; web/session orchestration is added in a later script. */
public final class AuthenticateLoginCredentialsUseCase {
  private final CredentialAuthenticationPort credentialAuthenticationPort;

  public AuthenticateLoginCredentialsUseCase(
      CredentialAuthenticationPort credentialAuthenticationPort) {
    this.credentialAuthenticationPort =
        Objects.requireNonNull(
            credentialAuthenticationPort, "credentialAuthenticationPort must not be null");
  }

  public Mono<CredentialAuthenticationResult> authenticate(
      CredentialAuthenticationCommand command) {
    return credentialAuthenticationPort.authenticate(command);
  }
}
