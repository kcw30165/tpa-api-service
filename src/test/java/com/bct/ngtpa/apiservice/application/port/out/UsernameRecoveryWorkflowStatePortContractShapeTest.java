package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** Pins the exact typed reactive persistence boundary of the Model B workflow-state sidecar. */
class UsernameRecoveryWorkflowStatePortContractShapeTest {

    @Test
    void exposesTypedReactiveLifecycleOperations() throws Exception {
        assertMethod("create", UsernameRecoveryWorkflowStateCreateCommand.class,
                UsernameRecoveryWorkflowStateMutationResult.class);
        assertMethod("load", AuthFlowSessionId.class,
                UsernameRecoveryWorkflowStateLoadResult.class);
        assertMethod("update", UsernameRecoveryWorkflowStateMutationCommand.class,
                UsernameRecoveryWorkflowStateMutationResult.class);
        assertMethod("delete", AuthFlowSessionId.class,
                UsernameRecoveryWorkflowStateDeleteResult.class);
    }

    private static void assertMethod(
            String name, Class<?> parameter, Class<?> resultType) throws Exception {
        Method method = UsernameRecoveryWorkflowStatePort.class.getMethod(name, parameter);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()).containsExactly(resultType);
    }
}
