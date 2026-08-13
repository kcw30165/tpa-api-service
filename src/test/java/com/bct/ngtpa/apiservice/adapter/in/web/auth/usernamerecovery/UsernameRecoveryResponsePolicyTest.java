package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the typed Task 023 policy output record: the {@link MutationResponse}
 * envelope plus the optional typed HTTP classification override consumed by
 * Task 024. The record never chooses an HTTP status and never inspects error
 * code strings.
 */
class UsernameRecoveryResponsePolicyTest {

    @Test
    void carriesBodyWithNoOverrideWhenOnlyBodySupplied() {
        MutationResponse<UsernameRecoveryWorkflowResultResponse<Void>> body =
                MutationResponse.success(
                        ApiStatus.SUCCESS,
                        new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.RETURN_TO_LOGIN, null),
                        List.of());

        UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> policy =
                new UsernameRecoveryResponsePolicy<>(body, null);

        assertThat(policy.body()).isSameAs(body);
        assertThat(policy.httpOverride()).isNull();
        assertThat(policy.hasHttpOverride()).isFalse();
    }

    @Test
    void carriesTypedOverrideWhenSupplied() {
        MutationResponse<UsernameRecoveryWorkflowResultResponse<Void>> body =
                MutationResponse.success(
                        ApiStatus.SUCCESS,
                        new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.RETURN_TO_LOGIN, null),
                        List.of());

        UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> policy =
                new UsernameRecoveryResponsePolicy<>(body, HttpStatusClassificationOverride.CONFLICT);

        assertThat(policy.body()).isSameAs(body);
        assertThat(policy.httpOverride()).isEqualTo(HttpStatusClassificationOverride.CONFLICT);
        assertThat(policy.hasHttpOverride()).isTrue();
    }

    @Test
    void nullBodyFailsClosed() {
        assertThatThrownBy(() -> new UsernameRecoveryResponsePolicy<>(null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
