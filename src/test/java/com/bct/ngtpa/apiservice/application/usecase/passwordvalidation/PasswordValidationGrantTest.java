package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PasswordValidationGrantTest {
  @Test
  void storedBindingRoundTripsWithoutRawSecretsAndMalformedStateFailsClosed() {
    Instant now = Instant.parse("2026-10-28T00:00:00Z");
    var grant = new PasswordValidationGrant("a".repeat(64),
        new PayloadDigest(1, "b".repeat(64)), now, now.plusSeconds(300));
    assertThat(PasswordValidationGrant.decode(grant.encode())).isEqualTo(grant);
    assertThat(grant.toString()).doesNotContain("a".repeat(64), "b".repeat(64));
    for (String invalid : new String[]{null, "", "consumed", "a|1|b|1|2", grant.encode()+"|extra",
        "a".repeat(64)+"|1|"+"b".repeat(64)+"|1|2"}) {
      assertThat(PasswordValidationGrant.decode(invalid)).isNull();
    }
  }

  @Test
  void malformedAndOversizedEncodingCannotBecomeAValidGrant() {
    assertThat(PasswordValidationGrant.decode("x".repeat(257))).isNull();
    assertThat(PasswordValidationGrant.decode("a".repeat(64)+"|bad|"+"b".repeat(64)+"|1|300001")).isNull();
    assertThatThrownBy(() -> new PasswordValidationGrant(null, new PayloadDigest(1, "b".repeat(64)),
        Instant.EPOCH, Instant.EPOCH.plusSeconds(300))).isInstanceOf(IllegalArgumentException.class);
  }
}
