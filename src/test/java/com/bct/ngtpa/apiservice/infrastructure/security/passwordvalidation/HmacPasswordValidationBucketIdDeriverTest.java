package com.bct.ngtpa.apiservice.infrastructure.security.passwordvalidation;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HmacPasswordValidationBucketIdDeriverTest {
  @Test
  void secretIsDefensivelyCopiedAndNamespacesDifferAcrossKeys() {
    byte[] key = new byte[32];
    var deriver = new HmacPasswordValidationBucketIdDeriver(key);
    String digest = deriver.derive("synthetic-member");
    key[0] = 1;
    assertThat(deriver.derive("synthetic-member")).isEqualTo(digest).matches("[0-9a-f]{64}");
    assertThat(new HmacPasswordValidationBucketIdDeriver(key).derive("synthetic-member"))
        .isNotEqualTo(digest);
  }

  @Test
  void weakKeysAndMissingIdentityFailClosed() {
    for (byte[] key : new byte[][] {null, new byte[0], new byte[31]}) {
      var unconfigured = new HmacPasswordValidationBucketIdDeriver(key);
      assertThatThrownBy(() -> unconfigured.derive("synthetic"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Password-validation limiter identity unavailable");
    }
    var deriver = new HmacPasswordValidationBucketIdDeriver(new byte[32]);
    assertThatThrownBy(() -> deriver.derive(null)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> deriver.derive(" ")).isInstanceOf(IllegalStateException.class);
  }
}
