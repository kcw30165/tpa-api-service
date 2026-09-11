package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Repository-wide guard for the shared-idempotency migration (P3-P6) and P7 cleanup.
 *
 * <p>After migration, no feature (Registration / Login / Forgot Password / Username Recovery) may
 * own its own idempotency lifecycle: the response-idempotency lifecycle is provided exclusively by
 * the shared {@code application.idempotency} capability ({@code IdempotencyExecutor} +
 * {@code IdempotencyPort}). This guard fails if any feature re-introduces the lifecycle clusters
 * removed in P7-B/P7-C.
 *
 * <p>The predicate keys on FEATURE-QUALIFIED lifecycle TYPE NAMES, not on the
 * {@code PROCESSING/COMPLETED/FAILED} enum shape, because that shape is also used by legitimate,
 * retained components (OTP-verify commit-state and message-delivery task status). Legitimate shared
 * public vocabulary (keys, actions, outcomes, reservation snapshots, persistence ids) and protected
 * replay / OTP reservation / handoff components are intentionally NOT matched.
 */
class SharedIdempotencyNoFeatureLocalLifecycleArchitectureTest {

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path SHARED_HOME =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/application/idempotency");
  private static final Path SHARED_REDIS =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/idempotency");

  // Feature-local idempotency LIFECYCLE artifacts (exact clusters removed in P7-B/P7-C).
  private static final Pattern FORBIDDEN_FEATURE_LIFECYCLE =
      Pattern.compile(
          "^(Registration|Login|ForgotPassword|UsernameRecovery)Idempotency"
              + "(Status|Record|StartCommand|CompleteCommand|FailCommand|UnknownOutcomeCommand|StartResult)$");

  // Feature-local Redis idempotency lifecycle adapters/records (Registration cluster removed in P7-B).
  private static final Pattern FORBIDDEN_REDIS_LIFECYCLE =
      Pattern.compile(
          "^Redis(Registration|Login|ForgotPassword|UsernameRecovery)Idempotency"
              + "(Operations|Record|ResponseSnapshotRecord)$");

  @Test
  void noFeatureLocalIdempotencyLifecycleTypesExist() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(MAIN)) {
      paths
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !p.startsWith(SHARED_HOME))
          .filter(p -> !p.startsWith(SHARED_REDIS))
          .forEach(
              p -> {
                String base = p.getFileName().toString().replace(".java", "");
                if (FORBIDDEN_FEATURE_LIFECYCLE.matcher(base).matches()
                    || FORBIDDEN_REDIS_LIFECYCLE.matcher(base).matches()) {
                  offenders.add(p.toString());
                }
              });
    }
    assertThat(offenders)
        .as(
            "feature-local idempotency lifecycle types must not exist; the shared"
                + " IdempotencyExecutor owns the lifecycle")
        .isEmpty();
  }

  @Test
  void legacyIdempotencyNamespaceLiteralIsConfinedToTheAllowlistedKeyFactory() throws IOException {
    String allow = "RedisLoginIdempotencyKeyFactory.java";
    List<Path> files;
    try (Stream<Path> paths = Files.walk(MAIN)) {
      files = paths.filter(p -> p.toString().endsWith(".java")).toList();
    }
    List<String> holders = new ArrayList<>();
    for (Path p : files) {
      String src = Files.readString(p);
      if (src.contains(":auth:idempotency:") && !p.getFileName().toString().equals(allow)) {
        holders.add(p.toString());
      }
    }
    assertThat(holders)
        .as(
            "legacy ':auth:idempotency:' namespace may only live in the OTP-verify/handoff key"
                + " factory")
        .isEmpty();
  }

  @Test
  void sharedIdempotencyCapabilityIsPresent() {
    assertThat(Files.exists(SHARED_HOME.resolve("IdempotencyExecutor.java"))).isTrue();
    assertThat(Files.exists(SHARED_HOME.resolve("IdempotencyPort.java"))).isTrue();
  }
}
