package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import org.junit.jupiter.api.Test;

class UsernameRecoverySchemeProjectionTest {

  private static UsernameRecoverySchemeProjection projection(
      UsernameRecoverySchemeRef schemeRef, boolean registered) {
    return new UsernameRecoverySchemeProjection(schemeRef, "DB", "DBA", registered);
  }

  @Test
  void registeredProjectionRetainsSchemeRefAndAccountData() {
    var projection = projection(new UsernameRecoverySchemeRef("sch_opaque-1"), true);

    assertThat(projection.schemeRef().value()).isEqualTo("sch_opaque-1");
    assertThat(projection.accountEnv()).isEqualTo("DB");
    assertThat(projection.trustCode()).isEqualTo("DBA");
    assertThat(projection.registered()).isTrue();
  }

  @Test
  void unregisteredProjectionMayBePreSessionWithoutSchemeRef() {
    // api-contract section 4 zero-registered projection carries "schemeRef": null.
    var projection = projection(null, false);

    assertThat(projection.schemeRef()).isNull();
    assertThat(projection.registered()).isFalse();
  }

  @Test
  void unregisteredProjectionMayCarrySessionBoundSchemeRef() {
    // api-contract section 4 successful start lists an unregistered scheme with a
    // session-bound opaque schemeRef ("sch_opaque-2").
    var projection = projection(new UsernameRecoverySchemeRef("sch_opaque-2"), false);

    assertThat(projection.schemeRef().value()).isEqualTo("sch_opaque-2");
    assertThat(projection.registered()).isFalse();
  }

  @Test
  void rejectsMissingOrBlankAccountEnvAndTrustCode() {
    var schemeRefForMissingAccountEnv = new UsernameRecoverySchemeRef("sch_1");
    assertThatThrownBy(
            () ->
                new UsernameRecoverySchemeProjection(
                    schemeRefForMissingAccountEnv, null, "DBA", true))
        .isInstanceOf(NullPointerException.class);

    var schemeRefForBlankAccountEnv = new UsernameRecoverySchemeRef("sch_1");
    assertThatThrownBy(
            () ->
                new UsernameRecoverySchemeProjection(
                    schemeRefForBlankAccountEnv, "  ", "DBA", true))
        .isInstanceOf(IllegalArgumentException.class);

    var schemeRefForBlankTrustCode = new UsernameRecoverySchemeRef("sch_1");
    assertThatThrownBy(
            () -> new UsernameRecoverySchemeProjection(schemeRefForBlankTrustCode, "DB", " ", true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsRegisteredProjectionWithoutSchemeRef() {
    assertThatThrownBy(() -> projection(null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("registered projection requires a schemeRef");
  }

  @Test
  void toStringDoesNotDiscloseSchemeRef() {
    var projection = projection(new UsernameRecoverySchemeRef("sch_secret-1"), true);

    assertThat(projection.toString())
        .doesNotContain("sch_secret-1")
        .contains("accountEnv=DB", "trustCode=DBA", "registered=true");
  }
}
