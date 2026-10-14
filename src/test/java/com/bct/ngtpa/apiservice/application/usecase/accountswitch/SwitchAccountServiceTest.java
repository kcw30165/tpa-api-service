package com.bct.ngtpa.apiservice.application.usecase.accountswitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AccountSwitchLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link SwitchAccountService}, now depending on the dedicated
 * {@link AccountSwitchLookupPort} instead of {@code PortalAccessContextPort}, resolving the
 * architecture-guard conflict identified against
 * {@code PortalAccessContextResponsibilityBoundaryGuardTest} and
 * {@code PortalAccessContextFinalMigrationEvidenceTest}.
 */
class SwitchAccountServiceTest {

  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String TARGET_REF = "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb";

  @Test
  void emitsAccountContextWhenPortResolvesTargetAccount() {
    AccountSwitchLookupPort port = mock(AccountSwitchLookupPort.class);
    AccountContext account = mock(AccountContext.class);
    when(port.resolveTargetAccount(TARGET_REF, SESSION_ID.value())).thenReturn(Mono.just(account));

    var service = new SwitchAccountService(port);
    var command = new SwitchAccountCommand(SESSION_ID, TARGET_REF);

    StepVerifier.create(service.switchAccount(command))
        .assertNext(result -> assertThat(result).isEqualTo(account))
        .verifyComplete();
  }

  @Test
  void propagatesAuthenticatedPortalContextResolutionExceptionWhenTargetAccountNotInSession() {
    AccountSwitchLookupPort port = mock(AccountSwitchLookupPort.class);
    when(port.resolveTargetAccount(TARGET_REF, SESSION_ID.value()))
        .thenReturn(Mono.error(new AuthenticatedPortalContextResolutionException()));

    var service = new SwitchAccountService(port);
    var command = new SwitchAccountCommand(SESSION_ID, TARGET_REF);

    StepVerifier.create(service.switchAccount(command))
        .expectError(AuthenticatedPortalContextResolutionException.class)
        .verify();
  }
}
