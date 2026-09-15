package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The per-profile client built inside {@code sendWithProfile} is the client that actually performs
 * APIM business calls. Filters registered only on the {@code apimWebClient} bean never run for
 * those calls, so correlation propagation and request logging must both be attached here.
 */
class ApimEffectiveClientFilterChainTest {

  private static final Path FACADE =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java");

  @Test
  void effectivePerProfileClientAppliesPropagationAndLoggingFilters() throws Exception {
    String source = Files.readString(FACADE).replaceAll("\\s+", "");

    assertThat(source)
        .as("context propagation filter must be attached to the cloned per-profile client")
        .contains("webClientBuilder.clone().baseUrl(baseUrl)".replaceAll("\\s+", ""))
        .contains(".filter(contextPropagationExchangeFilter.filter())".replaceAll("\\s+", ""))
        .contains(".filter(requestLoggingExchangeFilter.filter())".replaceAll("\\s+", ""));
  }
}
