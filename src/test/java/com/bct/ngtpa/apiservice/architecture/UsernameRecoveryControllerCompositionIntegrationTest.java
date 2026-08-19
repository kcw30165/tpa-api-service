package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.config.UsernameRecoveryFeatureConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cumulative 025-3 integration guard for controller, Spring composition and architecture. */
class UsernameRecoveryControllerCompositionIntegrationTest {
  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java");
  private static final Path CONFIG =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java");

  @Test
  void controllerPublishesExactlyStartRequestAndCancelUnderCanonicalPath() {
    assertThat(UsernameRecoveryController.class).hasAnnotation(RestController.class);
    RequestMapping base = UsernameRecoveryController.class.getAnnotation(RequestMapping.class);
    assertThat(base.value()).containsExactly("/api/v1/auth/username-recovery");
    assertThat(mappedPostPaths()).containsExactlyInAnyOrder("/start", "/request", "/cancel");
  }

  @Test
  void controllerConstructorComposesOnlyCommittedPortsMappersPoliciesAndCookieWriter() {
    Constructor<?>[] constructors = UsernameRecoveryController.class.getDeclaredConstructors();
    assertThat(constructors).hasSize(1);
    assertThat(Arrays.asList(constructors[0].getParameterTypes()))
        .containsExactly(
            StartUsernameRecoveryUseCase.class,
            UsernameRecoveryStartWebMapper.class,
            UsernameRecoveryStartResponsePolicy.class,
            RequestUsernameRecoveryUseCase.class,
            UsernameRecoveryRequestWebMapper.class,
            UsernameRecoveryRequestResponsePolicy.class,
            CancelUsernameRecoveryUseCase.class,
            UsernameRecoveryCancelWebMapper.class,
            UsernameRecoveryCancelResponsePolicy.class,
            UsernameRecoveryCookieWriter.class);
  }

  @Test
  void featureConfigurationPublishesEveryControllerDependencyAsConditionalBean() {
    assertThat(UsernameRecoveryFeatureConfig.class).hasAnnotation(Configuration.class);
    List<Class<?>> requiredReturnTypes =
        List.of(
            StartUsernameRecoveryUseCase.class,
            UsernameRecoveryStartWebMapper.class,
            UsernameRecoveryStartResponsePolicy.class,
            RequestUsernameRecoveryUseCase.class,
            UsernameRecoveryRequestWebMapper.class,
            UsernameRecoveryRequestResponsePolicy.class,
            CancelUsernameRecoveryUseCase.class,
            UsernameRecoveryCancelWebMapper.class,
            UsernameRecoveryCancelResponsePolicy.class,
            UsernameRecoveryCookieWriter.class);
    for (Class<?> requiredType : requiredReturnTypes) {
      List<Method> beanMethods =
          Arrays.stream(UsernameRecoveryFeatureConfig.class.getDeclaredMethods())
              .filter(method -> method.isAnnotationPresent(Bean.class))
              .filter(method -> requiredType.isAssignableFrom(method.getReturnType()))
              .toList();
      assertThat(beanMethods)
          .as("@Bean returning %s", requiredType.getSimpleName())
          .singleElement()
          .satisfies(
              method ->
                  assertThat(method)
                      .as("%s must remain override-safe", method.getName())
                      .matches(
                          candidate ->
                              candidate.isAnnotationPresent(ConditionalOnMissingBean.class)));
    }
  }

  @Test
  void controllerAndFeatureConfigPreserveCleanArchitectureSplit() throws Exception {
    String controller = Files.readString(CONTROLLER);
    String config = Files.readString(CONFIG);
    assertThat(controller)
        .doesNotContain(
            "adapter.out",
            "ReactiveStringRedisTemplate",
            "RedisTemplate",
            "StartUsernameRecoveryService",
            "RequestUsernameRecoveryService",
            "CancelUsernameRecoveryService",
            "ResponseCookie.from",
            "Set-Cookie")
        .containsIgnoringWhitespaces(
            "StartUsernameRecoveryUseCase",
            "RequestUsernameRecoveryUseCase",
            "CancelUsernameRecoveryUseCase",
            "WorkflowHttpResponses.toResponse");
    assertThat(config)
        .containsIgnoringWhitespaces(
            "RedisUsernameRecoveryIdempotencyAdapter",
            "StartUsernameRecoveryService",
            "RequestUsernameRecoveryService",
            "CancelUsernameRecoveryService")
        .doesNotContain("@RestController", "@RequestMapping", "@PostMapping");
  }

  @Test
  void controllerKeepsSecurityMaterialTransientAndUnexpectedFailuresCentralized() throws Exception {
    String source = Files.readString(CONTROLLER);
    assertThat(source)
        .containsIgnoringWhitespaces(
            "requestUseCase.request(command, csrfToken.trim(), clean(origin), clean(referer))",
            "cancelUseCase.cancel(command, csrfToken.trim(), clean(origin), clean(referer))",
            "onErrorResume(UsernameRecoveryStartException.class",
            "onErrorResume(UsernameRecoveryRequestException.class",
            "onErrorResume(UsernameRecoveryCancelException.class")
        .doesNotContain(
            "catch (Exception",
            "catch (RuntimeException",
            "onErrorResume(Exception.class",
            "onErrorResume(RuntimeException.class",
            "getMessage()",
            "errorCode.equals",
            "switch (errorCode)");
  }

  @Test
  void controllerRemainsConcreteFinalAndHasNoMutableStaticState() {
    int modifiers = UsernameRecoveryController.class.getModifiers();
    assertThat(Modifier.isFinal(modifiers)).isTrue();
    assertThat(Modifier.isAbstract(modifiers)).isFalse();
    assertThat(
            Arrays.stream(UsernameRecoveryController.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers())))
        .isTrue();
  }

  private static List<String> mappedPostPaths() {
    return Arrays.stream(UsernameRecoveryController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .map(
            method -> {
              PostMapping mapping = method.getAnnotation(PostMapping.class);
              return mapping.path().length > 0 ? mapping.path()[0] : mapping.value()[0];
            })
        .sorted()
        .toList();
  }
}
