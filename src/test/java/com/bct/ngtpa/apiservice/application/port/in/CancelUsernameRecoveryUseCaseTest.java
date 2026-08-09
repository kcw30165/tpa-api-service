package com.bct.ngtpa.apiservice.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CancelUsernameRecoveryUseCaseTest {

    @Test
    void exposesSingleTypedReactiveCancelMethod() {
        Class<?> port = CancelUsernameRecoveryUseCase.class;
        assertThat(Modifier.isInterface(port.getModifiers())).isTrue();
        assertThat(port.isAnnotationPresent(FunctionalInterface.class)).isTrue();

        Method[] methods = port.getDeclaredMethods();
        assertThat(methods).hasSize(1);

        Method method = methods[0];
        assertThat(method.getName()).isEqualTo("cancel");
        assertThat(method.getParameterTypes()).containsExactly(UsernameRecoveryCancelCommand.class);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);

        var parameterized = (ParameterizedType) method.getGenericReturnType();
        assertThat(parameterized.getRawType()).isEqualTo(Mono.class);
        assertThat(parameterized.getActualTypeArguments()).containsExactly(UsernameRecoveryCancelResult.class);
    }
}
