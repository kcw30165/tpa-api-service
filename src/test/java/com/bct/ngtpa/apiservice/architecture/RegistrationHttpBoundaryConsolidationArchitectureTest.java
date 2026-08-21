package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class RegistrationHttpBoundaryConsolidationArchitectureTest {
  private static final String TARGET_FQCN =
      "com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController";
  private static final Path TARGET_SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java");
  private static final Set<Path> LEGACY_SOURCES =
      Set.of(
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationUsernameController.java"));
  private static final Set<String> EXPECTED_POST_PATHS =
      Set.of("/start", "/complete", "/cancel", "/otp/resend", "/otp/verify", "/username/check");

  @Test
  void oneRegistrationControllerOwnsTheCompleteRegistrationHttpBoundary() {
    assertThat(TARGET_SOURCE).as("consolidated Registration source").exists();
    assertThat(LEGACY_SOURCES).allMatch(path -> !Files.exists(path));
    assertThatCode(() -> Class.forName(TARGET_FQCN)).doesNotThrowAnyException();

    Class<?> controller = loadTarget();
    assertThat(controller).hasAnnotation(RestController.class);
    Set<String> postPaths =
        Arrays.stream(controller.getDeclaredMethods())
            .map(method -> method.getAnnotation(PostMapping.class))
            .filter(annotation -> annotation != null)
            .flatMap(annotation -> Arrays.stream(mappingPaths(annotation)))
            .collect(Collectors.toSet());
    assertThat(postPaths).containsExactlyInAnyOrderElementsOf(EXPECTED_POST_PATHS);
    assertThat(postPaths).hasSize(6);
  }

  @Test
  void consolidatedControllerHasNoDirectOutboundInfrastructureDependency() {
    Class<?> controller = loadTarget();
    Set<String> forbiddenFragments = Set.of(".adapter.out.", ".redis.", ".apim.", ".persistence.");
    Arrays.stream(controller.getDeclaredFields())
        .map(field -> field.getType().getName())
        .forEach(typeName -> assertThat(forbiddenFragments).noneMatch(typeName::contains));
  }

  @Test
  void legacyOwnersAreAbsentWhileLoginAndForgotPasswordRemainSeparate() {
    assertThat(LEGACY_SOURCES).allMatch(path -> !Files.exists(path));
    assertThat(TARGET_SOURCE).exists();
    assertThat(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java"))
        .exists();
    assertThat(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordController.java"))
        .exists();
    assertThat(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordController.java"))
        .exists();
  }

  private static Class<?> loadTarget() {
    try {
      return Class.forName(TARGET_FQCN);
    } catch (ClassNotFoundException exception) {
      throw new AssertionError("Missing consolidated RegistrationController", exception);
    }
  }

  private static String[] mappingPaths(PostMapping annotation) {
    String[] paths = annotation.path();
    return paths.length > 0 ? paths : annotation.value();
  }
}
