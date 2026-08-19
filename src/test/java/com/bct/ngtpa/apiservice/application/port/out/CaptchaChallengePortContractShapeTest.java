package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CaptchaChallengePortContractShapeTest {

  @Test
  void captchaChallengePortExposesPersistenceAndTransitionOperations() throws Exception {
    Class<?> type = CaptchaChallengePort.class;
    assertThat(method(type, "create", CaptchaChallenge.class).getReturnType())
        .isEqualTo(Mono.class);
    assertThat(method(type, "load", CaptchaChallengeId.class).getReturnType())
        .isEqualTo(Mono.class);
    assertThat(
            method(type, "markVerified", CaptchaChallengeId.class, Instant.class).getReturnType())
        .isEqualTo(Mono.class);
    assertThat(
            method(type, "incrementFailedAttempt", CaptchaChallengeId.class, Instant.class)
                .getReturnType())
        .isEqualTo(Mono.class);
    assertThat(method(type, "markLocked", CaptchaChallengeId.class, Instant.class).getReturnType())
        .isEqualTo(Mono.class);
    assertThat(
            method(type, "markConsumed", CaptchaChallengeId.class, Instant.class).getReturnType())
        .isEqualTo(Mono.class);
    assertThat(
            method(type, "markRefreshed", CaptchaChallengeId.class, Instant.class).getReturnType())
        .isEqualTo(Mono.class);
    assertThat(method(type, "delete", CaptchaChallengeId.class).getReturnType())
        .isEqualTo(Mono.class);

    assertThat(method(type, "load", CaptchaChallengeId.class).getGenericReturnType().getTypeName())
        .contains(Optional.class.getName(), CaptchaChallenge.class.getName());
  }

  private static Method method(Class<?> type, String name, Class<?>... parameterTypes)
      throws Exception {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
        .findFirst()
        .orElseThrow();
  }
}
