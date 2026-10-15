package com.bct.ngtpa.apiservice.application.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AccountLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetAccountServiceTest {

  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String TARGET_REF = "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb";

  @Test
  void emitsAccountContextWhenPortResolvesTargetAccount() {
    AccountLookupPort port = mock(AccountLookupPort.class);
    AccountContext account = mock(AccountContext.class);
    when(port.getAccount(TARGET_REF, SESSION_ID.value())).thenReturn(Mono.just(account));

    var service = new GetAccountService(port);
    var command = new GetAccountCommand(SESSION_ID, TARGET_REF);

    StepVerifier.create(service.getAccount(command))
        .assertNext(result -> assertThat(result).isEqualTo(account))
        .verifyComplete();
  }

  @Test
  void propagatesAuthenticatedPortalContextResolutionExceptionWhenSessionInvalid() {
    AccountLookupPort port = mock(AccountLookupPort.class);
    when(port.getAccount(TARGET_REF, SESSION_ID.value()))
        .thenReturn(Mono.error(new AuthenticatedPortalContextResolutionException()));

    var service = new GetAccountService(port);
    var command = new GetAccountCommand(SESSION_ID, TARGET_REF);

    StepVerifier.create(service.getAccount(command))
        .expectError(AuthenticatedPortalContextResolutionException.class)
        .verify();
  }

  @Test
  void propagatesAccountNotInSessionExceptionWhenTargetAccountNotInSession() {
    AccountLookupPort port = mock(AccountLookupPort.class);
    when(port.getAccount(TARGET_REF, SESSION_ID.value()))
        .thenReturn(Mono.error(new AccountNotInSessionException()));

    var service = new GetAccountService(port);
    var command = new GetAccountCommand(SESSION_ID, TARGET_REF);

    StepVerifier.create(service.getAccount(command))
        .expectError(AccountNotInSessionException.class)
        .verify();
  }
}
