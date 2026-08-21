package com.bct.ngtpa.apiservice.application.dto.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsrfRefreshContractTest {
  @Test
  void resultAndCommandRedactSecrets() {
    var result = RefreshCsrfTokenResult.success(CsrfRefreshSessionType.PRE_AUTH, "raw-secret");
    var command =
        new RefreshCsrfTokenCommand("portal-secret", "flow-secret", "https://fe", null, "req-1");
    assertThat(result.toString()).doesNotContain("raw-secret");
    assertThat(command.toString()).doesNotContain("portal-secret", "flow-secret");
  }
}
