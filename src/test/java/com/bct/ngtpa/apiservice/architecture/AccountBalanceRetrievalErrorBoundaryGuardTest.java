package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class AccountBalanceRetrievalErrorBoundaryGuardTest {
  @Test
  void applicationExceptionIsWebNeutralAndPublicPolicyIsSanitized() throws Exception {
    String ex =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/exception/AccountBalanceRetrievalException.java"));
    String policy =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/accountbalance/AccountBalanceRetrievalResponsePolicy.java"));
    assertThat(ex)
        .doesNotContain("ApiStatus", "HttpStatus", "MutationResponse", "err.tpa", "err.ab");
    assertThat(policy)
        .doesNotContain(
            "DOWNSTREAM_REJECTED", "DOWNSTREAM_BUSINESS", "DOWNSTREAM_SYSTEM", "err.tpa");
  }
}
