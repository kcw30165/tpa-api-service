package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceNormalizedBoundaryGuardTest {
  @Test
  void normalizedBoundaryIsApplicationOwnedAndTransportPresentationNeutral() throws Exception {
    Path root =
        Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/accountbalance/normalized");
    assertThat(Files.exists(root)).isTrue();
    String source;
    try (var paths = Files.walk(root)) {
      source =
          paths
              .filter(Files::isRegularFile)
              .map(AccountBalanceNormalizedBoundaryGuardTest::read)
              .reduce("", String::concat);
    }
    assertThat(source).contains("record DisplaySourceKey", "record HoldingKey", "record FundKey");
    assertThat(source)
        .doesNotContain(
            "adapter.out.apim",
            "JsonProperty",
            "MutationResponse",
            "NumberFormat",
            "AccountBalanceResult",
            "groupingBy");
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
