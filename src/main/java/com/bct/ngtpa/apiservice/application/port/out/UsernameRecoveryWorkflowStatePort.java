package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import reactor.core.publisher.Mono;

/**
 * Outbound port for protected Username Recovery workflow-state sidecar persistence under the Model
 * B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}. Every operation is keyed by the
 * canonical {@link AuthFlowSessionId}; the sidecar is a separate Redis record whose TTL is owned by
 * the generic envelope.
 */
public interface UsernameRecoveryWorkflowStatePort {

    Mono<UsernameRecoveryWorkflowStateMutationResult> create(UsernameRecoveryWorkflowState state);

    Mono<UsernameRecoveryWorkflowStateLoadResult> load(AuthFlowSessionId authFlowSessionId);

    Mono<UsernameRecoveryWorkflowStateMutationResult> update(
            UsernameRecoveryWorkflowStateMutationCommand command);

    Mono<UsernameRecoveryWorkflowStateDeleteResult> delete(AuthFlowSessionId authFlowSessionId);
}
