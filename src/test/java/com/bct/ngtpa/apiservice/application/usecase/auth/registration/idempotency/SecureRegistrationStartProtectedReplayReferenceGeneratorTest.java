package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SecureRegistrationStartProtectedReplayReferenceGeneratorTest {
  @Test
  void generatesOpaqueUrlSafeReferencesWith256BitsOfSourceEntropy() {
    var generator = new SecureRegistrationStartProtectedReplayReferenceGenerator();
    var values = new HashSet<String>();
    for (int index = 0; index < 64; index++) {
      String value = generator.generate().value();
      assertThat(value).matches("[A-Za-z0-9_-]{43}");
      values.add(value);
    }
    assertThat(values).hasSize(64);
  }
}
