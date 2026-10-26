package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Scan-based ratchet guard over direct log.error/log.warn call sites in src/main/java. The
 * KNOWN_REMAINING count below is the exact count confirmed at commit
 * 75c8c37c780dddcc8eac23f54693c1235d223fd8; this test fails if the real count ever exceeds it, so
 * new direct call sites cannot be added without an explicit, reviewed increase to this constant.
 * Call-site migration to the shared logging boundary classes is M3 scope; this test only records
 * and ratchets the current state.
 */
class ErrorLogCallSiteRatchetArchitectureTest {

  private static final int KNOWN_REMAINING = 52;
  private static final Pattern CALL_SITE_PATTERN = Pattern.compile("\\blog\\.(error|warn)\\(");
  private static final Path SRC_MAIN_JAVA = Paths.get("src", "main", "java");

  @Test
  void directLogErrorOrWarnCallSitesDoNotExceedKnownRemaining() throws IOException {
    int actual = countCallSites();
    assertThat(actual)
        .as(
            "Direct log.error/log.warn call sites must not increase beyond the recorded "
                + "KNOWN_REMAINING ratchet (%s). Migrate new call sites to the shared logging "
                + "boundary instead of adding another direct call, or lower KNOWN_REMAINING if "
                + "call sites were removed.",
            KNOWN_REMAINING)
        .isLessThanOrEqualTo(KNOWN_REMAINING);
  }

  @Test
  void scanMechanismDetectsAKnownCallSitePattern() throws IOException {
    // Sanity check: the pattern itself must actually match real source text, proving the scan
    // is not silently matching zero files due to a path or regex mistake.
    int actual = countCallSites();
    assertThat(actual).isGreaterThan(0);
  }

  private static int countCallSites() throws IOException {
    try (Stream<Path> paths = Files.walk(SRC_MAIN_JAVA)) {
      List<Path> javaFiles =
          paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
      int total = 0;
      for (Path file : javaFiles) {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = CALL_SITE_PATTERN.matcher(content);
        while (matcher.find()) {
          total++;
        }
      }
      return total;
    }
  }
}
