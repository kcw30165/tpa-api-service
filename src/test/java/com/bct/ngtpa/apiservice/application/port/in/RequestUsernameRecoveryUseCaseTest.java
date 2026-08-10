package com.bct.ngtpa.apiservice.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequestUsernameRecoveryUseCaseTest {

    @Test
    void exposesRicherTypedReactiveRequestMethodAsSingleAbstractMethod() {
        Class<?> port = RequestUsernameRecoveryUseCase.class;
        assertThat(Modifier.isInterface(port.getModifiers())).isTrue();
        assertThat(port.isAnnotationPresent(FunctionalInterface.class)).isTrue();

        Method[] methods = port.getDeclaredMethods();
        List<Method> abstractMethods = Arrays.stream(methods)
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .toList();
        assertThat(abstractMethods).hasSize(1);

        Method method = abstractMethods.get(0);
        assertThat(method.getName()).isEqualTo("request");
        assertThat(method.getParameterTypes()).containsExactly(
                UsernameRecoveryRequestCommand.class, String.class, String.class, String.class);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);

        var parameterized = (ParameterizedType) method.getGenericReturnType();
        assertThat(parameterized.getRawType()).isEqualTo(Mono.class);
        assertThat(parameterized.getActualTypeArguments()).containsExactly(UsernameRecoveryRequestResult.class);
    }

    @Test
    void singleArgumentCompatibilityMethodRemainsAndFailsClosed() {
        Class<?> port = RequestUsernameRecoveryUseCase.class;
        List<Method> defaultMethods = Arrays.stream(port.getDeclaredMethods())
                .filter(method -> !Modifier.isAbstract(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .toList();
        assertThat(defaultMethods).hasSize(1);
        assertThat(defaultMethods.get(0).getName()).isEqualTo("request");
        assertThat(defaultMethods.get(0).getParameterTypes())
                .containsExactly(UsernameRecoveryRequestCommand.class);

        RequestUsernameRecoveryUseCase portInstance = (command, rawCsrfToken, origin, referer) ->
                Mono.just(new UsernameRecoveryRequestResult(
                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                        UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "p****c@ah.ozk",
                        UsernameRecoveryDestinationType.EMAIL));

        StepVerifier.create(portInstance.request(new UsernameRecoveryRequestCommand(
                        new AuthFlowSessionId("afs_opaque-1"),
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "idem_12345678")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                    assertThat(((UsernameRecoveryRequestException) error).reason())
                            .isEqualTo(UsernameRecoveryRequestException.Reason.SECURITY_CONTEXT_REQUIRED);
                })
                .verify();
    }
}
