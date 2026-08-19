package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class OtpChallengePortContractShapeTest {

  @Test
  void otpChallengePortExposesLifecycleOperations() {
    Method create = method("createChallenge", CreateOtpChallengeCommand.class);
    Method verify = method("verifyChallenge", VerifyOtpChallengeCommand.class);
    Method replace = method("replaceChallenge", ReplaceOtpChallengeCommand.class);
    Method invalidate = method("invalidateChallenge", InvalidateOtpChallengeCommand.class);

    assertThat(create.getReturnType()).isEqualTo(Mono.class);
    assertThat(verify.getReturnType()).isEqualTo(Mono.class);
    assertThat(replace.getReturnType()).isEqualTo(Mono.class);
    assertThat(invalidate.getReturnType()).isEqualTo(Mono.class);

    assertThat(create.getGenericReturnType().getTypeName())
        .isEqualTo(Mono.class.getName() + "<" + CreateOtpChallengeResult.class.getName() + ">");
    assertThat(verify.getGenericReturnType().getTypeName())
        .isEqualTo(Mono.class.getName() + "<" + VerifyOtpChallengeResult.class.getName() + ">");
    assertThat(replace.getGenericReturnType().getTypeName())
        .isEqualTo(Mono.class.getName() + "<" + ReplaceOtpChallengeResult.class.getName() + ">");
    assertThat(invalidate.getGenericReturnType().getTypeName())
        .isEqualTo(Mono.class.getName() + "<java.lang.Void>");
  }

  private Method method(String name, Class<?> parameterType) {
    return Arrays.stream(OtpChallengePort.class.getMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .filter(
            candidate ->
                Arrays.equals(candidate.getParameterTypes(), new Class<?>[] {parameterType}))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing method: " + name));
  }
}
