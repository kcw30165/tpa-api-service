package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApimFailureClassifierArchitectureTest {
  private static final Path CLASSIFIER =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimFailureClassifier.java");

  @Test
  void classifierRemainsPureAndContainsNoFeatureOwnedBusinessCodes() throws Exception {
    String source = Files.readString(CLASSIFIER);
    assertThat(source)
        .doesNotContain("@Component", "@Service", "reactor.", "Mono<", "Flux<")
        .doesNotContain("err.tpa.", "err.login.", "err.registration.", "err.forgot-password.")
        .doesNotContain("ApiStatus", "HttpStatus", "WorkflowErrorType", "application.usecase", "adapter.in.web", "shared.logging")
        .doesNotContain("DOWNSTREAM_BUSINESS");
  }
}
