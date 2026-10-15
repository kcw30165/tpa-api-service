package com.bct.ngtpa.apiservice.application.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AccountLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetAccountsServiceTest {

  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void emitsAccountListWhenPortResolvesAccounts() {
    AccountLookupPort port = mock(AccountLookupPort.class);
    AccountContext a1 = mock(AccountContext.class);
    AccountContext a2 = mock(AccountContext.class);
    when(port.listAccounts(SESSION_ID.value())).thenReturn(Mono.just(List.of(a1, a2)));

    var service = new GetAccountsService(port);
    var command = new GetAccountsCommand(SESSION_ID);

    StepVerifier.create(service.getAccounts(command))
        .assertNext(result -> assertThat(result).containsExactly(a1, a2))
        .verifyComplete();
  }

  @Test
  void emitsEmptyListWhenSessionHasNoAccounts() {
    AccountLookupPort port = mock(AccountLookupPort.class);
    when(port.listAccounts(SESSION_ID.value())).thenReturn(Mono.just(List.of()));

    var service = new GetAccountsService(port);
    var command = new GetAccountsCommand(SESSION_ID);

    StepVerifier.create(service.getAccounts(command))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
  }

  @Test
  void propagatesAuthenticatedPortalContextResolutionExceptionWhenSessionInvalid() {
    AccountLookupPort port = mock(AccountLookupPort.class);
    when(port.listAccounts(SESSION_ID.value()))
        .thenReturn(Mono.error(new AuthenticatedPortalContextResolutionException()));

    var service = new GetAccountsService(port);
    var command = new GetAccountsCommand(SESSION_ID);

    StepVerifier.create(service.getAccounts(command))
        .expectError(AuthenticatedPortalContextResolutionException.class)
        .verify();
  }
}
