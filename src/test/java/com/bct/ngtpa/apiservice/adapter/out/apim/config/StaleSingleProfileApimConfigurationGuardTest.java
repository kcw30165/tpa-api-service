package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class StaleSingleProfileApimConfigurationGuardTest {

  @Test
  void sharedConfigurationKeepsOnlyProfileScopedApimCredentials() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains("credential-profiles:")
        .contains("profile-id: apim-non-account")
        .contains("profile-id: db")
        .contains("profile-id: jp")
        .contains("profile-id: tb")
        .contains("${APIM_NON_ACCOUNT_CLIENT_ID:}")
        .contains("${APIM_DB_CLIENT_ID:}")
        .contains("${APIM_JP_CLIENT_ID:}")
        .contains("${APIM_TB_CLIENT_ID:}")
        .doesNotContain("${APIM_CLIENT_ID:")
        .doesNotContain("${APIM_CLIENT_SECRET:")
        .doesNotContain("${APIM_BASE_URL:");

    assertThat(countOccurrences(yaml, "${APIM_TOKEN_URI:}"))
        .as("shared token URI remains available to all four active profiles")
        .isGreaterThanOrEqualTo(4);
  }

  private static int countOccurrences(String value, String token) {
    int count = 0;
    int offset = 0;
    while ((offset = value.indexOf(token, offset)) >= 0) {
      count++;
      offset += token.length();
    }
    return count;
  }
}
