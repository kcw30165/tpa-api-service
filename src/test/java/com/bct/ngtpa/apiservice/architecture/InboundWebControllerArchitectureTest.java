package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.CaptchaController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.LogoutController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.contribution.ContributionController;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.AuthenticatedPortalContextWebExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.internal.referencedate.ReferenceDateController;
import com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.referencedata.ReferenceDataController;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class InboundWebControllerArchitectureTest {
  private static final Path OLD_FLAT =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller");
  private static final Path MARKER =
      Path.of("src/test/resources/architecture/inbound-web-controller-architecture.marker");
  private static final List<Class<?>> ENDPOINT_CONTROLLERS =
      List.of(
          CaptchaController.class,
          ForgotPasswordController.class,
          LoginController.class,
          LogoutController.class,
          RegistrationController.class,
          UsernameRecoveryController.class,
          ContributionController.class,
          NotificationController.class,
          PersonalInformationController.class,
          ReferenceDataController.class,
          ReferenceDateController.class);

  @Test
  void finalArchitectureEnforcementMarkerExists() throws IOException {
    assertThat(MARKER).exists();
    assertThat(Files.readString(MARKER)).contains("controller-refactoring-stage-007=enabled");
  }

  @Test
  void oldFlatProductionControllerPackageIsEmpty() throws IOException {
    if (!Files.exists(OLD_FLAT)) {
      return;
    }
    try (var files = Files.list(OLD_FLAT)) {
      assertThat(files.filter(path -> path.toString().endsWith(".java")).toList()).isEmpty();
    }
  }

  @Test
  void finalOwnersHaveApprovedBoundaryAnnotations() {
    assertThat(ApiExceptionHandler.class).hasAnnotation(RestControllerAdvice.class);
    assertThat(
            AuthenticatedPortalContextWebExceptionHandler.class.isAnnotationPresent(
                RestControllerAdvice.class))
        .isFalse();
    assertThat(
            AuthenticatedPortalContextWebExceptionHandler.class.isAnnotationPresent(
                ControllerAdvice.class))
        .isFalse();
    assertThat(ENDPOINT_CONTROLLERS)
        .allMatch(type -> type.isAnnotationPresent(RestController.class));
    assertThat(ENDPOINT_CONTROLLERS)
        .noneMatch(type -> type.isAnnotationPresent(ControllerAdvice.class));
  }

  @Test
  void endpointControllersHaveNoForbiddenDirectDependency() {
    List<String> forbidden =
        List.of(".adapter.out.", ".redis.", ".apim.", ".persistence.", "WebClient");
    ENDPOINT_CONTROLLERS.forEach(
        controller ->
            Arrays.stream(controller.getDeclaredFields())
                .map(field -> field.getType().getName())
                .forEach(type -> assertThat(forbidden).noneMatch(type::contains)));
  }

  @Test
  void effectiveMethodAndPathPairsAreUniqueAndComplete() {
    Set<String> routes = new HashSet<>();
    List<String> duplicates = new ArrayList<>();
    ENDPOINT_CONTROLLERS.forEach(
        controller -> {
          String[] roots = mappingPaths(controller.getAnnotation(RequestMapping.class));
          if (roots.length == 0) roots = new String[] {""};
          for (Method method : controller.getDeclaredMethods()) {
            for (String route : methodRoutes(roots, method)) {
              if (!routes.add(route)) duplicates.add(route);
            }
          }
        });
    assertThat(duplicates).isEmpty();
    assertThat(routes).hasSize(36);
    assertThat(routes)
        .contains(
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/registration/start",
            "POST /api/v1/auth/forgot-password/start",
            "POST /api/v1/internal/reference-date/refresh",
            "GET /api/v1/reference-data/countries");
  }

  @Test
  void cohesiveJourneysHaveSingleOwnersAndRemainSeparated() throws IOException {
    assertThat(routeCount(LoginController.class)).isEqualTo(5);
    assertThat(routeCount(RegistrationController.class)).isEqualTo(6);
    assertThat(routeCount(ForgotPasswordController.class)).isEqualTo(8);
    String forgotPassword =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordController.java"));
    assertThat(forgotPassword).doesNotContain("ChangePassword", "change-password");
  }

  private static int routeCount(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
        .mapToInt(InboundWebControllerArchitectureTest::mappingCount)
        .sum();
  }

  private static int mappingCount(Method method) {
    return methodRoutes(new String[] {""}, method).size();
  }

  private static List<String> methodRoutes(String[] roots, Method method) {
    List<String> result = new ArrayList<>();
    add(result, roots, "GET", mappingPaths(method.getAnnotation(GetMapping.class)));
    add(result, roots, "POST", mappingPaths(method.getAnnotation(PostMapping.class)));
    add(result, roots, "PUT", mappingPaths(method.getAnnotation(PutMapping.class)));
    add(result, roots, "PATCH", mappingPaths(method.getAnnotation(PatchMapping.class)));
    add(result, roots, "DELETE", mappingPaths(method.getAnnotation(DeleteMapping.class)));
    return result;
  }

  private static void add(List<String> result, String[] roots, String verb, String[] paths) {
    if (paths.length == 0) return;
    for (String root : roots) {
      for (String path : paths) {
        result.add(verb + " " + normalize(root, path));
      }
    }
  }

  private static String normalize(String root, String path) {
    String joined = ("/" + root + "/" + path).replaceAll("/+", "/");
    return joined.length() > 1 && joined.endsWith("/")
        ? joined.substring(0, joined.length() - 1)
        : joined;
  }

  private static String[] mappingPaths(RequestMapping mapping) {
    if (mapping == null) return new String[0];
    return mapping.path().length > 0 ? mapping.path() : mapping.value();
  }

  private static String[] mappingPaths(GetMapping mapping) {
    if (mapping == null) return new String[0];
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? new String[] {""} : paths;
  }

  private static String[] mappingPaths(PostMapping mapping) {
    if (mapping == null) return new String[0];
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? new String[] {""} : paths;
  }

  private static String[] mappingPaths(PutMapping mapping) {
    if (mapping == null) return new String[0];
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? new String[] {""} : paths;
  }

  private static String[] mappingPaths(PatchMapping mapping) {
    if (mapping == null) return new String[0];
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? new String[] {""} : paths;
  }

  private static String[] mappingPaths(DeleteMapping mapping) {
    if (mapping == null) return new String[0];
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? new String[] {""} : paths;
  }
}
