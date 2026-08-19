package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import org.junit.jupiter.api.Test;

class CaptchaAnswerHasherPortContractShapeTest {

  @Test
  void captchaAnswerHasherPortDoesNotExposePlainAnswer() throws Exception {
    var hash = CaptchaAnswerHasherPort.class.getDeclaredMethod("hash", String.class, boolean.class);
    var matches =
        CaptchaAnswerHasherPort.class.getDeclaredMethod(
            "matches", String.class, CaptchaAnswerHash.class, boolean.class);

    assertThat(hash.getReturnType()).isEqualTo(CaptchaAnswerHash.class);
    assertThat(matches.getReturnType()).isEqualTo(boolean.class);
    assertThat(matches.getParameterTypes()[1]).isEqualTo(CaptchaAnswerHash.class);
  }
}
