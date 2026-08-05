package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OtpChallengeModelTest {

    @Test
    void otpStatusDefinesRequiredLifecycleStates() {
        assertThat(Arrays.stream(OtpStatus.values()).map(Enum::name).toList())
                .containsExactly(
                        "CREATED",
                        "VERIFIED",
                        "CONSUMED",
                        "INVALIDATED",
                        "EXPIRED",
                        "LOCKED");
    }

    @Test
    void otpScenarioSupportsRegistrationAndLogin() {
        assertThat(Arrays.stream(OtpScenario.values()).map(Enum::name).toList())
                .containsExactly("REGISTRATION", "LOGIN", "FORGOT_PASSWORD");
    }

    @Test
    void otpChannelSupportsSmsAndEmail() {
        assertThat(Arrays.stream(OtpChannel.values()).map(Enum::name).toList())
                .containsExactly("SMS", "EMAIL");
    }

    @Test
    void otpChallengeDoesNotExposeRawCodeField() {
        List<String> componentNames = Arrays.stream(OtpChallenge.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .contains("codeHash")
                .doesNotContain("rawCode", "code", "otpCode", "plainCode");
        assertThat(Arrays.stream(OtpChallenge.class.getMethods())
                .map(method -> method.getName())
                .filter(name -> List.of("rawCode", "code", "otpCode", "plainCode").contains(name))
                .toList()).isEmpty();
    }

    @Test
    void createOtpChallengeResultMayExposeRawCodeForDelivery() {
        RecordComponent[] components = CreateOtpChallengeResult.class.getRecordComponents();

        assertThat(components).hasSize(4);
        assertThat(components[0].getName()).isEqualTo("otpChallengeId");
        assertThat(components[0].getType()).isEqualTo(String.class);
        assertThat(components[1].getName()).isEqualTo("rawCode");
        assertThat(components[1].getType()).isEqualTo(String.class);
        assertThat(components[2].getName()).isEqualTo("expiresAt");
        assertThat(components[2].getType()).isEqualTo(Instant.class);
        assertThat(components[3].getName()).isEqualTo("expiresInSeconds");
        assertThat(components[3].getType()).isEqualTo(int.class);
    }

    @Test
    void createOtpChallengeCommandCarriesPolicyInputs() {
        RecordComponent[] components = CreateOtpChallengeCommand.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
                .containsExactly(
                        "scenario",
                        "workflowRef",
                        "channel",
                        "destinationHash",
                        "length",
                        "numericOnly",
                        "ttlSeconds",
                        "maxVerifyAttempts",
                        "maxResendAttempts",
                        "now");
        assertThat(components[0].getType()).isEqualTo(OtpScenario.class);
        assertThat(components[2].getType()).isEqualTo(OtpChannel.class);
        assertThat(components[9].getType()).isEqualTo(Instant.class);
    }

    @Test
    void verifyOtpChallengeResultCanRepresentAllOutcomes() {
        assertThat(Arrays.stream(OtpVerificationOutcome.values()).map(Enum::name).toList())
                .containsExactly(
                        "VALID",
                        "INVALID",
                        "EXPIRED",
                        "LOCKED",
                        "INVALIDATED",
                        "ALREADY_VERIFIED",
                        "NOT_FOUND");
    }
}