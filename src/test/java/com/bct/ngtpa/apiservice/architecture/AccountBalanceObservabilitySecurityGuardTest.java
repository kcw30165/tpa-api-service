package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceObservabilitySecurityGuardTest {
  @Test
  void telemetryPortsExposeOnlySafeScalarAndEnumValues() throws Exception {
    String account =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/AccountBalanceObservabilityPort.java"));
    String metadata =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/GeneralMetadataObservabilityPort.java"));
    String combined = account + metadata;

    assertThat(combined).contains("durationMs", "validatedCount", "requestId");
    assertThat(combined)
        .doesNotContain(
            "GetAccountBalanceCommand",
            "GeneralMetadataScope",
            "ResolvedGeneralMetadataSet",
            "RawAccountBalanceData",
            "Throwable",
            "Map<",
            "URL");
  }

  @Test
  void adaptersNeverAcceptOrLogForbiddenPayloads() throws Exception {
    String account =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/AccountBalanceObservabilityLogger.java"));
    String metadata =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/GeneralMetadataObservabilityLogger.java"));
    String apim =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/client/ApimRequestLoggingExchangeFilter.java"));
    String cache =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/metadata/RedisGeneralMetadataCacheAdapter.java"));

    assertThat(account + metadata)
        .doesNotContain(
            "accountRef",
            "policyNo",
            "certNo",
            "actorUserId",
            "factsheetUrl",
            "requestBody",
            "responseBody",
            "Throwable");
    assertThat(apim).doesNotContain("request.body", "response.body", "requestBody", "responseBody");
    assertThat(cache)
        .contains("SHA-256", "digest(scope.trustCode()", "scope.policyNo())")
        .doesNotContain("ngtpa:general-metadata:DB:DBA", "ngtpa:general-metadata:DB:00000008802");
  }
}
