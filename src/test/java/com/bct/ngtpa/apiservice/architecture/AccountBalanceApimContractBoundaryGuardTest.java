package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AccountBalanceApimContractBoundaryGuardTest {
  @Test
  void contractContainsNoExecutorAdapterOrPresentationLogic() throws Exception {
    Path root =
        Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance/dto");
    String contentValidator =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance/AccountBalanceApimContentValidator.java"));

    String contracts;
    try (Stream<Path> paths = Files.walk(root)) {
      contracts =
          paths
              .filter(Files::isRegularFile)
              .sorted()
              .map(AccountBalanceApimContractBoundaryGuardTest::read)
              .reduce(contentValidator, String::concat);
    }

    assertThat(contracts)
        .doesNotContain(
            "ApimExchangeExecutor",
            "MutationResponse",
            "NumberFormat",
            "factsheetUrl",
            "@Component",
            "@Service");
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read Account Balance APIM contract", exception);
    }
  }
}
