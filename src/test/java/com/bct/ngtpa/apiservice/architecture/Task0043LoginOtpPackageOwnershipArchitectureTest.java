package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpPackageOwnershipArchitectureTest {
        @Test
        void loginHasNoDedicatedOtpDtoTreeOrChannelVocabulary() throws Exception {
                Path forbidden = Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/otp");
                if (Files.exists(forbidden)) {
                        try (var files = Files.walk(forbidden)) {
                                assertThat(files.filter(path -> path.toString().endsWith(".java")).toList())
                                                .isEmpty();
                        }
                }
                assertThat(Files.exists(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/SendLoginOtpCommand.java")))
                                .isTrue();
                assertThat(Files.exists(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/otp/OtpChannel.java")))
                                .isTrue();
                assertThat(Files.exists(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginOtpChannel.java")))
                                .isFalse();
        }

        @Test
        void loginOtpActionsUseTheExistingLoginIdempotencyVocabulary() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/idempotency/LoginIdempotencyAction.java"));
                assertThat(source).contains("CREDENTIALS", "OTP_SEND", "OTP_RESEND", "OTP_VERIFY");
                assertThat(Files.exists(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/otp/idempotency")))
                                .isFalse();
        }
}
