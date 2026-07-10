package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationHardcodedPolicyArchitectureGuardTest {

    @Test
    void authRegistrationControllersDoNotOwnIdempotencyTtlConstants() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"));

        assertThat(controller)
                .doesNotContain("Duration.ofMinutes(5)")
                .doesNotContain("DEFAULT_IDEMPOTENCY_TTL")
                .contains("RegistrationIdempotencyProperties")
                .contains("otpResendTtl()")
                .contains("otpVerifyTtl()");
    }

    @Test
    void authFlowCookieWriterDoesNotHardcodeProductionCookiePolicy() throws Exception {
        String writer = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/config/AuthFlowCookieWriter.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/config/RegistrationStartWebConfig.java"));

        assertThat(writer)
                .contains("AuthFlowSessionProperties")
                .doesNotContain("Duration.ofMinutes(15)")
                .doesNotContain("new AuthFlowCookieProperties(\"auth_flow_sid\", \"/api/v1/auth\"");
        assertThat(config)
                .contains("AuthFlowCookieWriter.from(authFlowSessionProperties)")
                .doesNotContain("defaultWriter()");
    }

    @Test
    void registrationGatewayUsesTypedAuthFlowSessionPropertiesNotValueFallbacks() throws Exception {
        String gateway = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/config/RegistrationStartGatewayConfig.java"));

        assertThat(gateway)
                .contains("AuthFlowSessionProperties")
                .doesNotContain("@Value(\"${auth-flow-session.scenarios.registration.ttl-seconds:1800}\")")
                .doesNotContain("@Value(\"${auth-flow-session.csrf.max-age-seconds:1800}\")");
    }

    @Test
    void otpIdempotencyCommandsDoNotApplyLocalTtlFallbacks() throws Exception {
        for (String path : List.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/ResendRegistrationOtpCommand.java",
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/VerifyRegistrationOtpCommand.java")) {
            String source = Files.readString(Path.of(path));
            assertThat(source)
                    .contains("Objects.requireNonNull(idempotencyTtl")
                    .doesNotContain("Duration.ofMinutes(5)");
        }
    }
}
