package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpRateLimitAndDeliveryOwnershipTest {
    @Test void otpAdapterChecksDestinationRateLimitForInitialSendAndResend() throws Exception {
        String source=Files.readString(find("RegistrationOtpChallengeAdapter.java"));
        assertThat(source).contains("RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH");
        assertThat(count(source,"enforceOtpSendRateLimit(channel, destinationHash)")).isGreaterThanOrEqualTo(2);
        assertThat(source).contains("RegistrationOtpSendRateLimitedException");
    }
    @Test void resendDistinguishesCooldownFromDestinationSendLimit() throws Exception {
        String source=Files.readString(find("ResendRegistrationOtpService.java"));
        assertThat(source).contains("COOLDOWN_ACTIVE").contains("SEND_RATE_LIMITED")
                .contains("err.registration.otp.sendRateLimited");
    }
    @Test void publicResendFailedCodeIsNotEmitted() throws Exception {
        String source=Files.readString(find("ResendRegistrationOtpService.java"));
        assertThat(source).doesNotContain("err.registration.otp.resendFailed");
    }
    private Path find(String name) throws Exception {
        try(var paths=Files.walk(Path.of("src/main/java"))){return paths.filter(p->p.getFileName().toString().equals(name)).findFirst().orElseThrow();}
    }
    private int count(String value,String token){int count=0,index=0;while((index=value.indexOf(token,index))>=0){count++;index+=token.length();}return count;}
}
