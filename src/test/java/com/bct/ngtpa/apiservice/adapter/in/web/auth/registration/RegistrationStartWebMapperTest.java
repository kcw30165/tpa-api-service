package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationStartWebMapperTest {
    private final RegistrationStartWebMapper mapper = new RegistrationStartWebMapper(Duration.ofMinutes(5));

    @Test
    void registrationStartMapsRequestToUseCaseCommand() {
        var request = new RegistrationStartRequest("GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_secret");
        StartRegistrationCommand command = mapper.toCommand(request, "idem_abc123", "req-123", "en-HK");

        assertThat(command.idempotencyKey()).isEqualTo(new RegistrationIdempotencyKey("idem_abc123"));
        assertThat(command.identityType()).isEqualTo("GPFS");
        assertThat(command.identityValue()).isEqualTo("1234567890");
        assertThat(command.dateOfBirth()).isEqualTo("31/01/1980");
        assertThat(command.email()).isEqualTo("member@example.com");
        assertThat(command.mobile()).isNull();
        assertThat(command.acceptLanguage()).isEqualTo("en-HK");
        assertThat(command.correlationId()).isEqualTo("req-123");
        assertThat(command.toString()).doesNotContain("1234567890", "member@example.com", "cap_secret", "req-123", "idem_abc123");
    }

    @Test
    void responseToStringDoesNotExposeCsrfTokenOrSessionId() {
        var result = new StartRegistrationResult(StartRegistrationResultStatus.STARTED, WorkflowNextStep.VERIFY_OTP, "m****r@example.com", 300, 60, 5, "csrf_secret", "afs_secret", List.of(), List.of(), RegistrationIdempotencyStartOutcome.STARTED, null);
        var response = mapper.toResponse(result);

        assertThat(response.csrfToken()).isEqualTo("csrf_secret");
        assertThat(response.toString()).doesNotContain("csrf_secret", "afs_secret", "authFlowSessionId", "otpChallengeId", "otpCode");
    }
}
