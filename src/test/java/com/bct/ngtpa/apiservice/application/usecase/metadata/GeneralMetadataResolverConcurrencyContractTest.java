package com.bct.ngtpa.apiservice.application.usecase.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GeneralMetadataResolverConcurrencyContractTest {
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/metadata/GeneralMetadataResolverService.java");

  @Test
  void sameKeyWorkUsesRepositoryInFlightLifecyclePattern() throws Exception {
    String source = Files.readString(SERVICE);
    assertThat(source)
        .contains(
            "ConcurrentHashMap<>()",
            "new InFlightKey(query.scope(), query.requiredKeys())",
            "computeIfAbsent(",
            ".cache()",
            ".doFinally(",
            ".doOnCancel(")
        .contains("inFlight.remove(key, created.get())", "inFlight.remove(key, shared)")
        .doesNotContain(".block(", ".subscribe(", "retryWhen", "repeatWhen");
  }

  @Test
  void targetedMutationUsesOneCasAndOneBoundedReread() throws Exception {
    String source = Files.readString(SERVICE);
    assertThat(source)
        .contains(".replaceIfUnchanged(", "query.scope()", "cached.orElseThrow()", "replacement")
        .contains("outcome == GeneralMetadataCacheReplaceResult.APPLIED")
        .contains("recoverAfterConflict(query)");
    String recovery = methodSource(source, "recoverAfterConflict(");
    assertThat(recovery)
        .contains(".read(query.scope())", "new GeneralMetadataConcurrencyException()")
        .doesNotContain("while (", "for (", "retryWhen", "repeatWhen");
  }

  private static String methodSource(String source, String signature) {
    int start = source.indexOf(signature);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int brace = source.indexOf('{', start);
    int depth = 0;
    for (int index = brace; index < source.length(); index++) {
      if (source.charAt(index) == '{') depth++;
      if (source.charAt(index) == '}' && --depth == 0) return source.substring(start, index + 1);
    }
    throw new IllegalStateException("method boundary not found");
  }

  @Test
  void technicalFailureAndDiagnosticsContainNoScopeIdentifiers() {
    String message = new GeneralMetadataConcurrencyException().getMessage();
    assertThat(message)
        .isEqualTo("Concurrent general metadata update could not be resolved")
        .doesNotContain("trust", "policy", "account", "redis", "key");
  }
}
