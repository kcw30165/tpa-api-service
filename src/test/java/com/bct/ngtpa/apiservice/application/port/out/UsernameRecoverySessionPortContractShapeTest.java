package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UsernameRecoverySessionPortContractShapeTest {

    @Test
    void exposesTypedReactiveLifecycleOperations() throws Exception {
        assertMethod("create", UsernameRecoverySession.class,
                UsernameRecoverySessionMutationResult.class);
        assertMethod("load", UsernameRecoverySessionId.class,
                UsernameRecoverySessionLoadResult.class);
        assertMethod("update", UsernameRecoverySessionMutationCommand.class,
                UsernameRecoverySessionMutationResult.class);
        assertMethod("delete", UsernameRecoverySessionId.class,
                UsernameRecoverySessionDeleteResult.class);
    }

    private static void assertMethod(
            String name, Class<?> parameter, Class<?> resultType) throws Exception {
        Method method = UsernameRecoverySessionPort.class.getMethod(name, parameter);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()).containsExactly(resultType);
    }
}
