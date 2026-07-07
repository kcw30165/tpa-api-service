package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CaptchaUseCaseResultTest {

    @Test
    void useCasesDoNotRequireAuthFlowCookieCsrfIdempotencyOrAccountRef() {
        assertThat(componentNames(CreateCaptchaChallengeCommand.class))
                .doesNotContain("authFlowSessionId", "csrfToken", "idempotencyKey", "accountRef");
        assertThat(componentNames(VerifyCaptchaChallengeCommand.class))
                .doesNotContain("authFlowSessionId", "csrfToken", "idempotencyKey", "accountRef");
        assertThat(componentNames(RefreshCaptchaChallengeCommand.class))
                .doesNotContain("authFlowSessionId", "csrfToken", "idempotencyKey", "accountRef");
    }

    @Test
    void createRefreshAndVerifyResultsDoNotExposeSensitiveFields() {
        assertThat(componentNames(CreateCaptchaChallengeResult.class))
                .containsExactly("captchaChallengeId", "image", "expiresInSeconds")
                .doesNotContain("answer", "answerHash");
        assertThat(componentNames(RefreshCaptchaChallengeResult.class))
                .containsExactly("captchaChallengeId", "image", "expiresInSeconds")
                .doesNotContain("answer", "answerHash");
        assertThat(componentNames(VerifyCaptchaChallengeResult.class))
                .containsExactly("outcome", "verified", "captchaChallengeId", "remainingAttempts")
                .doesNotContain("answer", "answerHash", "image");
    }

    @Test
    void createRefreshAndVerifyToStringDoNotExposeSensitiveValues() {
        var challengeId = new CaptchaChallengeId("cap_abcdefghijklmnop");
        var image = new CaptchaImage("base64-image", "image/png");

        assertThat(new CreateCaptchaChallengeResult(challengeId, image, 123).toString())
                .doesNotContain(challengeId.value(), image.imageBase64());
        assertThat(new RefreshCaptchaChallengeResult(challengeId, image, 123).toString())
                .doesNotContain(challengeId.value(), image.imageBase64());
        assertThat(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.INVALID, false, null, 4).toString())
                .doesNotContain(challengeId.value(), image.imageBase64(), "answerHash");
    }

    @Test
    void captchaUseCasesDoNotHardcodeUserFacingMessages() throws Exception {
        String createSource = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/captcha/CreateCaptchaChallengeService.java"));
        String verifySource = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/captcha/VerifyCaptchaService.java"));
        String refreshSource = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/captcha/RefreshCaptchaService.java"));

        assertThat(createSource + verifySource + refreshSource)
                .doesNotContain("Invalid captcha")
                .doesNotContain("Captcha expired")
                .doesNotContain("Too many attempts")
                .doesNotContain("驗證碼");
    }

    private static java.util.List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}