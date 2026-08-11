package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Pins the exact outbound port shape of the Model B workflow-state sidecar: every operation is
 * keyed by the canonical {@link AuthFlowSessionId} and returns a typed reactive result. Mirrors the
 * {@code UsernameRecoverySessionPortContractShapeTest} convention (Task 011) with workflow-state
 * vocabulary.
 */
class UsernameRecoveryWorkflowStatePortContractShapeTest {

    @Test
    void exposesTypedReactiveLifecycleOperations() throws Exception {
        assertMethod("create", UsernameRecoveryWorkflowState.class,
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
