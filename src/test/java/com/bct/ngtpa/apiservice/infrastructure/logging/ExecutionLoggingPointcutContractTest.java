package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.SharedCompleteRegistrationService;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

class ExecutionLoggingPointcutContractTest {

  private static final String EXPRESSION =
      "execution(public * com.bct.ngtpa.apiservice.application.usecase..*Service.execute(..))";

  @Test
  void automaticUseCaseLoggingExcludesPrivateExecuteHelpersOnFinalServices() {
    AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
    pointcut.setExpression(EXPRESSION);

    Method privateExecute =
        java.util.Arrays.stream(SharedCompleteRegistrationService.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("execute"))
            .findFirst()
            .orElseThrow();

    assertThat(Modifier.isFinal(SharedCompleteRegistrationService.class.getModifiers())).isTrue();
    assertThat(Modifier.isPrivate(privateExecute.getModifiers())).isTrue();
    assertThat(pointcut.matches(privateExecute, SharedCompleteRegistrationService.class)).isFalse();
  }
}
