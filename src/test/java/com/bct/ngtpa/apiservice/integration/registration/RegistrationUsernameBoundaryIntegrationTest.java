package com.bct.ngtpa.apiservice.integration.registration;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.*;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationUsernameBoundaryIntegrationTest {

    @Test
    void selectedTrustModelContainsOnlyTrustCodeAndAccountEnv() {
        assertThat(componentNames(RegistrationSelectedTrust.class))
                .containsExactly("trustCode", "accountEnv")
                .doesNotContain("policyNo", "certNo");
    }

    @Test
    void resultStatusMapsIntendedNextSteps() {
        assertThat(CheckRegistrationUsernameResult.nextStepFor(
                CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE))
                .isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
        assertThat(CheckRegistrationUsernameResult.nextStepFor(
                CheckRegistrationUsernameResultStatus.VALIDATION_FAILED))
                .isEqualTo(WorkflowNextStep.USERNAME_CHECK);
        assertThat(CheckRegistrationUsernameResult.nextStepFor(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED))
                .isEqualTo(WorkflowNextStep.USERNAME_CHECK);
    }

    @Test
    @RegistrationTestCase("TC-060")
    void usernameCheckCommandHasNoIdempotencyInputsAndRedactsSecrets() {
        var command = new CheckRegistrationUsernameCommand(
                new AuthFlowSessionId("afs_secret"),
                "csrf_secret",
                new RegistrationSelectedTrust("TL", "ENV1"),
                "abc1@",
                "https://example.test",
                "https://example.test/register",
                "en",
                "req-1");

        assertThat(componentNames(CheckRegistrationUsernameCommand.class))
                .doesNotContain("idempotencyKey", "idempotencyTtl");
        assertThat(Arrays.stream(CheckRegistrationUsernameCommand.class.getRecordComponents())
                .map(component -> component.getType().getName()))
                .doesNotContain(Duration.class.getName());
        assertThat(command.toString()).doesNotContain("afs_secret", "csrf_secret", "abc1@");
    }

    @Test
    void availabilityBoundaryDistinguishesRejectedFromTechnicalFailure() {
        var rejected = new RegistrationUsernameAvailabilityResult(
                RegistrationUsernameAvailabilityStatus.REJECTED,
                "err.tpa.username.taken");
        var failed = new RegistrationUsernameAvailabilityResult(
                RegistrationUsernameAvailabilityStatus.DOWNSTREAM_FAILED,
                null);

        assertThat(rejected.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.REJECTED);
        assertThat(rejected.downstreamErrorCode()).isEqualTo("err.tpa.username.taken");
        assertThat(failed.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.DOWNSTREAM_FAILED);
        assertThat(failed.downstreamErrorCode()).isNull();
    }

    private static java.util.List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
