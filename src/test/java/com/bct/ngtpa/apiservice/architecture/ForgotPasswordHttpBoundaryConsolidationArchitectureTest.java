package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ForgotPasswordHttpBoundaryConsolidationArchitectureTest {
  private static final Path TARGET_SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordController.java");
  private static final Set<Path> LEGACY_SOURCES =
      Set.of(
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpController.java"));
  private static final Set<String> EXPECTED_POST_PATHS =
      Set.of(
          "/start",
          "/identity-check",
          "/details-check",
          "/details",
          "/cancel",
          "/complete",
          "/otp/resend",
          "/otp/verify");

  @Test
  void oneControllerOwnsTheCompleteForgotPasswordHttpBoundary() {
    assertThat(TARGET_SOURCE).exists();
    assertThat(LEGACY_SOURCES).allMatch(path -> !Files.exists(path));
    assertThatCode(() -> Class.forName(ForgotPasswordController.class.getName()))
        .doesNotThrowAnyException();
    assertThat(ForgotPasswordController.class).hasAnnotation(RestController.class);
    RequestMapping root = ForgotPasswordController.class.getAnnotation(RequestMapping.class);
    assertThat(root.value()).containsExactly("/api/v1/auth/forgot-password");

    Set<String> paths =
        Arrays.stream(ForgotPasswordController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(PostMapping.class))
            .filter(annotation -> annotation != null)
            .flatMap(annotation -> Arrays.stream(mappingPaths(annotation)))
            .collect(Collectors.toSet());
    assertThat(paths).containsExactlyInAnyOrderElementsOf(EXPECTED_POST_PATHS);
    assertThat(paths).hasSize(8);
  }

  @Test
  void consolidatedControllerHasNoDirectOutboundInfrastructureDependency() {
    Set<String> forbidden = Set.of(".adapter.out.", ".redis.", ".apim.", ".persistence.");
    Arrays.stream(ForgotPasswordController.class.getDeclaredFields())
        .map(field -> field.getType().getName())
        .forEach(type -> assertThat(forbidden).noneMatch(type::contains));
  }

  @Test
  void changePasswordAndUsernameRecoveryRemainOutsideTheBoundary() throws Exception {
    String source = Files.readString(TARGET_SOURCE);
    assertThat(source)
        .doesNotContain("ChangePassword", "change-password", "UsernameRecovery")
        .doesNotContain("Account-Ref");
    assertThat(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java"))
        .exists();
  }

  private static String[] mappingPaths(PostMapping annotation) {
    return annotation.path().length > 0 ? annotation.path() : annotation.value();
  }
}
