package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CaptchaImageGeneratorPortContractShapeTest {

  @Test
  void captchaImageGeneratorPortAllowsInternalOrExternalImplementation() throws Exception {
    var method = CaptchaImageGeneratorPort.class.getDeclaredMethod("generate", CaptchaPolicy.class);
    assertThat(method.getReturnType()).isEqualTo(Mono.class);
    assertThat(method.getGenericReturnType().getTypeName())
        .contains(GeneratedCaptcha.class.getName());
    assertThat(
            Arrays.stream(GeneratedCaptcha.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
        .containsExactly("answer", "image");
    assertThat(
            new GeneratedCaptcha(
                    "AbC123x",
                    new com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage(
                        "iVBORw0KGgo", "image/png"))
                .toString())
        .doesNotContain("AbC123x", "iVBORw0KGgo");
  }
}
