package com.bct.ngtpa.apiservice.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CancelUsernameRecoveryUseCaseTest {

    @Test
    void exposesRicherTypedReactiveCancelMethodAsSingleAbstractMethod() {
        Class<?> port = CancelUsernameRecoveryUseCase.class;
        assertThat(Modifier.isInterface(port.getModifiers())).isTrue();
        assertThat(port.isAnnotationPresent(FunctionalInterface.class)).isTrue();

        Method[] methods = port.getDeclaredMethods();
        List<Method> abstractMethods = Arrays.stream(methods)
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .toList();
        assertThat(abstractMethods).hasSize(1);

        Method method = abstractMethods.get(0);
        assertThat(method.getName()).isEqualTo("cancel");
        assertThat(method.getParameterTypes()).containsExactly(
                UsernameRecoveryCancelCommand.class, String.class, String.class, String.class);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);

        var parameterized = (ParameterizedType) method.getGenericReturnType();
        assertThat(parameterized.getRawType()).isEqualTo(Mono.class);
        assertThat(parameterized.getActualTypeArguments()).containsExactly(UsernameRecoveryCancelResult.class);
    }

    @Test
    void singleArgumentCompatibilityMethodRemainsAndFailsClosed() {
        Class<?> port = CancelUsernameRecoveryUseCase.class;
        List<Method> defaultMethods = Arrays.stream(port.getDeclaredMethods())
                .filter(method -> !Modifier.isAbstract(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .toList();
        assertThat(defaultMethods).hasSize(1);
        assertThat(defaultMethods.get(0).getName()).isEqualTo("cancel");
        assertThat(defaultMethods.get(0).getParameterTypes())
                .containsExactly(UsernameRecoveryCancelCommand.class);

        CancelUsernameRecoveryUseCase portInstance = (command, rawCsrfToken, origin, referer) ->
                Mono.just(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));

        StepVerifier.create(portInstance.cancel(new UsernameRecoveryCancelCommand(
                        new AuthFlowSessionId("afs_opaque-1"),
                        "idem_12345678")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(UsernameRecoveryCancelException.Reason.SECURITY_CONTEXT_REQUIRED);
                })
                .verify();
    }
}
