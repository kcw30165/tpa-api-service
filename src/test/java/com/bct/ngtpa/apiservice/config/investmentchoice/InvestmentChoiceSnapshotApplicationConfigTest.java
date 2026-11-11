package com.bct.ngtpa.apiservice.config.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.idempotency.JacksonTransientJsonCodec;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class InvestmentChoiceSnapshotApplicationConfigTest {

  @Test
  void wiresTheCacheServiceFromItsCollaborators() {
    var service =
        new InvestmentChoiceSnapshotApplicationConfig()
            .investmentChoiceSnapshotCacheService(
                mock(AuthenticatedSessionPort.class),
                mock(AuthenticatedSessionProtectorPort.class),
                mock(AuthenticatedSessionConcurrencyService.class),
                new JacksonTransientJsonCodec(),
                Clock.systemUTC());

    assertThat(service).isNotNull();
  }
}
