package com.bct.ngtpa.apiservice.config.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegistrationSharedIdempotencyConfigTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(RegistrationSharedIdempotencyConfig.class);

  @Test
  void composesOneRegistrationSharedIdentityAndFingerprintFactory() {
    context.run(
        application -> {
          assertThat(application).hasSingleBean(RegistrationSharedIdempotencyIdentityFactory.class);
          assertThat(application).hasSingleBean(RegistrationSharedFingerprintFactory.class);
        });
  }

  @Test
  void preservesTestProvidedOverrides() {
    var identity = new RegistrationSharedIdempotencyIdentityFactory();
    var fingerprint = new RegistrationSharedFingerprintFactory();
    context
        .withBean(RegistrationSharedIdempotencyIdentityFactory.class, () -> identity)
        .withBean(RegistrationSharedFingerprintFactory.class, () -> fingerprint)
        .run(
            application -> {
              assertThat(application.getBean(RegistrationSharedIdempotencyIdentityFactory.class))
                  .isSameAs(identity);
              assertThat(application.getBean(RegistrationSharedFingerprintFactory.class))
                  .isSameAs(fingerprint);
            });
  }
}
