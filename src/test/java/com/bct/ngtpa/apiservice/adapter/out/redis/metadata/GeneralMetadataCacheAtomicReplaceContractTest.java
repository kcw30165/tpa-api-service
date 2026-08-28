package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GeneralMetadataCacheAtomicReplaceContractTest {
  private static final Path ADAPTER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/metadata/RedisGeneralMetadataCacheAdapter.java");
  private static final Path OPERATIONS =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/metadata/ReactiveGeneralMetadataRedisOperations.java");

  @Test
  void applicationPortExposesReactiveCapabilityScopedReplacement() throws Exception {
    Method method =
        GeneralMetadataCachePort.class.getMethod(
            "replaceIfUnchanged",
            com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope.class,
            com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet.class,
            com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet.class);
    assertThat(method.getReturnType()).isEqualTo(Mono.class);
    assertThat(GeneralMetadataCacheReplaceResult.values())
        .containsExactly(
            GeneralMetadataCacheReplaceResult.APPLIED,
            GeneralMetadataCacheReplaceResult.CONFLICT,
            GeneralMetadataCacheReplaceResult.MISSING);
  }

  @Test
  void adapterUsesSameDocumentMappingForExpectedAndReplacement() throws Exception {
    String source = Files.readString(ADAPTER);
    assertThat(source)
        .contains(
            "encode(GeneralMetadataCacheDocument.from(expected))",
            "encode(GeneralMetadataCacheDocument.from(replacement))",
            "operations.replaceIfUnchanged(key(scope), values[0], values[1])");
  }

  @Test
  void adapterRejectsScopeMismatchBeforeRedisDelegation() throws Exception {
    String method = methodSource(Files.readString(ADAPTER), "replaceIfUnchanged(");
    assertThat(method)
        .contains(
            "!scope.equals(expected.scope())",
            "!scope.equals(replacement.scope())",
            "IllegalArgumentException(\"Metadata scopes must match\")")
        .containsSubsequence(
            "if (!scope.equals(expected.scope())",
            "return Mono.error",
            "operations.replaceIfUnchanged");
  }

  @Test
  void adapterPropagatesAtomicOutcomeWithoutTranslation() throws Exception {
    String method = methodSource(Files.readString(ADAPTER), "replaceIfUnchanged(");
    assertThat(method)
        .contains("operations.replaceIfUnchanged")
        .doesNotContain("switch", "GeneralMetadataCacheReplaceResult.APPLIED", "onErrorResume");
  }

  @Test
  void physicalOperationContainsNoSensitiveScopeFieldsOrLogging() throws Exception {
    String source = Files.readString(OPERATIONS);
    assertThat(source)
        .doesNotContain("trustCode", "policyNo", "LOGGER", "log.")
        .contains("Cache replace operation failed");
  }

  private static String methodSource(String source, String signature) {
    int start = source.indexOf(signature);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int brace = source.indexOf('{', start);
    int depth = 0;
    for (int index = brace; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') depth++;
      if (current == '}' && --depth == 0) return source.substring(start, index + 1);
    }
    throw new IllegalStateException("method boundary not found");
  }
}
