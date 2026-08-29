package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AccountBalanceArchitectureConfigurationGuardTest {
  private static final Path MAIN = Path.of("src/main/java");
  private static final Path RESOURCES = Path.of("src/main/resources");

  @Test
  void runtimeConfigurationOwnsBothApimPathsAndMetadataTtl() throws Exception {
    String resources = treeText(RESOURCES);
    String properties = read("src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/metadata/GeneralMetadataCacheProperties.java");
    assertThat(resources).contains("account-balance", "general-metadata", "PT20M");
    assertThat(properties).contains("Duration", "PT20M");
  }

  @Test
  void adaptersUseCanonicalExecutorAndApprovedClassifications() throws Exception {
    String account = read("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance/ApimAccountBalanceSourceAdapter.java");
    String metadata = read("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java");
    assertThat(account + metadata)
        .contains("ApimExchangeExecutor", "ACCOUNT_ENV", "SAFE_BUSINESS_CALL")
        .doesNotContain("WebClient", ".block(", ".subscribe(");
  }

  @Test
  void sharedMetadataRemainsFeatureNeutralAndHasOneRuntimeResolver() throws Exception {
    String metadata = treeText(Path.of("src/main/java/com/bct/ngtpa/apiservice/application"));
    String config = read("src/main/java/com/bct/ngtpa/apiservice/config/metadata/GeneralMetadataRuntimeConfig.java");
    assertThat(metadata).doesNotContain("application.usecase.metadata.accountbalance");
    assertThat(config).contains("new GeneralMetadataResolverService(");
    assertThat(config.split("new GeneralMetadataResolverService\\(", -1)).hasSize(2);
  }

  @Test
  void forbiddenRuntimeAndWebContractsRemainAbsent() throws Exception {
    String accountBalance = treeText(Path.of("src/main/java/com/bct/ngtpa/apiservice"));
    String controller = read("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AccountBalanceController.java");
    assertThat(controller).doesNotContain("Idempotency-Key", "ObjectMapper.setDefaultPropertyInclusion");
    assertThat(accountBalance).doesNotContain("AccountBalanceMetadataStartupLoader", "GeneralMetadataStartupLoader");
  }

  @Test
  void correlationAndReactiveRulesRemainNarrow() throws Exception {
    String account = read("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance/ApimAccountBalanceSourceAdapter.java");
    String metadata = read("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java");
    assertThat(account + metadata).doesNotContain("Idempotency-Key", ".block(", ".subscribe(");
    assertThat(treeText(MAIN)).contains("X-Request-Id");
  }

  private static String read(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  private static String treeText(Path root) throws Exception {
    StringBuilder text = new StringBuilder();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        if (path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")) {
          text.append(Files.readString(path)).append('\n');
        }
      }
    }
    return text.toString();
  }
}
