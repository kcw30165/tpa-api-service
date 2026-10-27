package com.bct.ngtpa.apiservice.config.personalinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.idempotency.JacksonTransientJsonCodec;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class PersonalInformationConfigCacheApplicationConfigTest {

  @Test
  void wiresTheCacheServiceFromItsCollaborators() {
    var service =
        new PersonalInformationConfigCacheApplicationConfig()
            .personalInformationConfigCacheService(
                mock(AuthenticatedSessionPort.class),
                mock(AuthenticatedSessionProtectorPort.class),
                mock(AuthenticatedSessionConcurrencyService.class),
                new JacksonTransientJsonCodec(),
                Clock.systemUTC());

    assertThat(service).isNotNull();
  }
}
