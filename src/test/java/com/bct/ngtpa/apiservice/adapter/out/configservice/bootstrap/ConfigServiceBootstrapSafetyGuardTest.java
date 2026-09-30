package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-level guards for the bootstrap package.
 *
 * <p>The loader runs before the Spring context exists, so it must not depend on Spring beans (plan
 * section 3), and it must never log credentials or configuration values (plan section 4).
 *
 * <p>These guards deliberately analyse code with comments stripped. An earlier version matched raw
 * substrings and produced false positives on a javadoc reference and on the code that legitimately
 * SETS an Authorization header -- setting an auth header is not logging a credential.
 */
class ConfigServiceBootstrapSafetyGuardTest {

  private static final Path DIR =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/configservice/bootstrap");

  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");

  /** Source with all comments removed, so javadoc cannot trip a guard. */
  private static String code(Path source) throws Exception {
    String body = Files.readString(source);
    body = BLOCK_COMMENT.matcher(body).replaceAll(" ");
    return LINE_COMMENT.matcher(body).replaceAll(" ");
  }

  private List<Path> sources() throws Exception {
    try (var stream = Files.list(DIR)) {
      return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
    }
  }

  @Test
  void bootstrapCodeDoesNotImportOrInjectSpringBeans() throws Exception {
    for (Path source : sources()) {
      String body = code(source);

      assertThat(body)
          .as("%s must not import a context-scoped bean", source.getFileName())
          .doesNotContain(
              "import com.bct.ngtpa.apiservice.adapter.out.configservice.config.ConfigServiceProperties",
              "import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort",
              "import com.bct.ngtpa.apiservice.adapter.out.configservice.ConfigServiceWebClientAdapter");

      assertThat(body)
          .as("%s must not be a Spring bean or use injection", source.getFileName())
          .doesNotContain("@Component", "@Service", "@Configuration", "@Autowired", "@Bean");

      assertThat(body)
          .as("%s must not use the reactive WebClient stack at bootstrap", source.getFileName())
          .doesNotContain("org.springframework.web.reactive.function.client");
    }
  }

  @Test
  void noLogStatementCarriesACredentialOrConfigurationValue() throws Exception {
    for (Path source : sources()) {
      for (String line : code(source).split("\n")) {
        String trimmed = line.trim();
        boolean isLogCall =
            trimmed.contains("LOG.")
                || trimmed.contains("log.")
                || trimmed.contains("System.out")
                || trimmed.contains("System.err");
        if (!isLogCall) {
          continue;
        }
        assertThat(trimmed)
            .as("log statement in %s must not carry a secret or value", source.getFileName())
            .doesNotContain(
                "password()",
                "username()",
                "getPassword()",
                "configValue()",
                "response.body()",
                "token");
      }
    }
  }

  @Test
  void responseBodyIsNeverPlacedInAnExceptionMessage() throws Exception {
    for (Path source : sources()) {
      for (String line : code(source).split("\n")) {
        if (!line.contains("new IllegalStateException")
            && !line.contains("new IllegalArgumentException")) {
          continue;
        }
        assertThat(line)
            .as("exception message in %s must not embed the response body", source.getFileName())
            .doesNotContain("body", "configValue");
      }
    }
  }

  @Test
  void postProcessorIsRegisteredUnderTheNonDeprecatedInterfaceName() throws Exception {
    Path imports =
        Path.of(
            "src/main/resources/META-INF/spring/"
                + "org.springframework.boot.EnvironmentPostProcessor.imports");

    assertThat(Files.exists(imports))
        .as("registration must use the non-deprecated org.springframework.boot FQN")
        .isTrue();
    assertThat(Files.readString(imports))
        .contains(
            "com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap"
                + ".ConfigServiceEnvironmentPostProcessor");
  }

  @Test
  void loaderDoesNotUseTheDeprecatedEnvironmentPostProcessorPackage() throws Exception {
    for (Path source : sources()) {
      assertThat(code(source))
          .as(
              "%s must not use the deprecated org.springframework.boot.env type",
              source.getFileName())
          .doesNotContain("org.springframework.boot.env.EnvironmentPostProcessor");
    }

    Path deprecatedImports =
        Path.of(
            "src/main/resources/META-INF/spring/"
                + "org.springframework.boot.env.EnvironmentPostProcessor.imports");
    assertThat(Files.exists(deprecatedImports))
        .as("the deprecated registration filename must not remain")
        .isFalse();
  }
}
