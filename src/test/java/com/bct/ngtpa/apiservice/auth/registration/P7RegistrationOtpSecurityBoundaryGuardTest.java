package com.bct.ngtpa.apiservice.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationUnregisteredTrust;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P7RegistrationOtpSecurityBoundaryGuardTest {

    @Test
    void p7ResponsesAndSnapshotsDoNotExposeKnownSensitiveFieldNames() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        String resendJson = objectMapper.writeValueAsString(new RegistrationOtpResendWebMapper(null)
                .toMutationResponse(new ResendRegistrationOtpResult(
                        ResendRegistrationOtpResultStatus.RESENT,
                        WorkflowNextStep.VERIFY_OTP,
                        "m****r@example.com",
                        300,
                        60,
                        4,
                        List.of(),
                        List.of(),
                        RegistrationIdempotencyStartOutcome.STARTED,
                        null)));

        String verifyJson = objectMapper.writeValueAsString(new RegistrationOtpVerifyWebMapper(null)
                .toMutationResponse(new VerifyRegistrationOtpResult(
                        VerifyRegistrationOtpResultStatus.VERIFIED,
                        WorkflowNextStep.USERNAME_CHECK,
                        List.of(new RegistrationUnregisteredTrust("OI", "00000008802", "2", "JPM")),
                        null,
                        List.of(),
                        List.of(),
                        RegistrationIdempotencyStartOutcome.STARTED,
                        null)));

        assertThat(resendJson + verifyJson).doesNotContain(
                "auth_flow_sid",
                "csrfToken",
                "csrfTokenHash",
                "activeOtpChallengeId",
                "otpChallengeId",
                "rawOtpCode",
                "rawCode",
                "identityValue",
                "dateOfBirth",
                "encryptedContextPlaintext",
                "Set-Cookie",
                "Authorization",
                "123456",
                "member@example.com",
                "3024102",
                "04/04/1982");
    }

    @Test
    void p7DoesNotHardcodeUserFacingMessages() throws Exception {
        String sources = p7ProductionSources();
        assertThat(sources).doesNotContain(
                "Invalid verification code",
                "OTP expired",
                "Too many attempts",
                "A new verification code has been sent",
                "No eligible trusts",
                "驗證碼");
    }

    @Test
    void readmeDocumentsP7ResendVerifyOnly() throws Exception {
        Path readme = Path.of("README.md");
        assertThat(Files.exists(readme)).isTrue();
        String text = Files.readString(readme);
        assertThat(text).contains(
                "Registration OTP Resend and Verify",
                "POST /api/v1/auth/registration/otp/resend",
                "POST /api/v1/auth/registration/otp/verify",
                "auth_flow_sid",
                "X-XSRF-TOKEN",
                "Idempotency-Key",
                "VERIFY_OTP",
                "USERNAME_CHECK",
                "SELECT_TRUST",
                "RegistrationLookupPort",
                "TRPSearchUnregisteredMember");
        assertThat(text).contains("username/check", "registration/complete", "registration/cancel", "auth-flow resume");
    }


    private String p7ProductionSources() throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Path root : new Path[] {
                Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp"),
                Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration"),
                Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller")}) {
            if (!Files.exists(root)) { continue; }
            try (var files = Files.walk(root)) {
                for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    builder.append(Files.readString(path)).append('\n');
                }
            }
        }
        return builder.toString();
    }
}
