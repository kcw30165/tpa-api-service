package com.bct.ngtpa.apiservice.adapter.out.security.login.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SecureRandomLoginAccountRefGeneratorTest {
  @Test
  void generatesOpaqueUrlSafeReferencesWithConfiguredEntropy() {
    var generator = new SecureRandomLoginAccountRefGenerator("acc", 24);
    var unique = new HashSet<String>();
    for (int index = 0; index < 500; index++) {
      String value = generator.generate().value();
      assertThat(value).matches(Pattern.compile("^acc_[A-Za-z0-9_-]{32}$"));
      unique.add(value);
    }
    assertThat(unique).hasSize(500);
  }
}
