package com.bct.ngtpa.apiservice.config.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginByHandoffUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TransientJsonPortCompositionTest {
  private static final String HMAC_PROPERTY =
      "auth-workflows.login.idempotency.persistence-hmac-secret="
          + "dGVzdC1sb2dpbi1wZXJzaXN0ZW5jZS1obWFjLXNlY3RldC0zMi1ieXRlcw==";

  @Test
  void suppliesLoginJsonPortWhenNoSharedPortExists() {
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(TransientJsonPort.class);
              assertThat(context.getBeanNamesForType(TransientJsonPort.class))
                  .containsExactly("loginTransientJsonPort");
            });
  }

  @Test
  void backsOffWhenAnotherFeatureAlreadySuppliesSharedPort() {
    TransientJsonPort existing = mock(TransientJsonPort.class);
    runner()
        .withBean("existingTransientJsonPort", TransientJsonPort.class, () -> existing)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(TransientJsonPort.class);
              assertThat(context.getBean(TransientJsonPort.class)).isSameAs(existing);
              assertThat(context).doesNotHaveBean("loginTransientJsonPort");
            });
  }

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withPropertyValues(HMAC_PROPERTY)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(
            RecoverCommittedLoginByHandoffUseCase.class,
            () -> mock(RecoverCommittedLoginByHandoffUseCase.class))
        .withUserConfiguration(LoginSharedIdempotencyConfig.class);
  }
}
