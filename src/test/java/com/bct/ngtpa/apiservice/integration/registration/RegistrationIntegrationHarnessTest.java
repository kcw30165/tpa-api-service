package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCatalog;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationIntegrationHarnessTest {

    private static final String CHALLENGE_ID = "cap_abcdefghijklmnop";
    private static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII=";

    @Test
    @RegistrationTestCase("TC-001")
    void captchaChallengeProofOfLifeUsesHttpBoundaryAndMutationResponse() {
        WebTestClient client = captchaClient();

        EntityExchangeResult<byte[]> exchange = client.post()
                .uri("/api/v1/auth/captcha/challenge")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "0041-tc-001-request")
                .header("Accept-Language", "en")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, "0041-tc-001-request")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.result.captchaChallengeId").isEqualTo(CHALLENGE_ID)
                .jsonPath("$.result.imageBase64").isEqualTo(PNG_BASE64)
                .jsonPath("$.result.mimeType").isEqualTo("image/png")
                .jsonPath("$.result.expiresInSeconds").isEqualTo(300)
                .jsonPath("$.messages").isArray()
                .jsonPath("$.errors").isArray()
                .returnResult();

        byte[] png = Base64.getDecoder().decode(PNG_BASE64);
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(new String(exchange.getResponseBodyContent()))
                .doesNotContain("answerHash", "captchaAnswer", "auth_flow_sid", "Account-Ref");
    }

    @Test
    void catalogueContainsEightyTwoUniqueCases() throws Exception {
        RegistrationTestCatalog catalog = RegistrationTestCatalog.loadDefault();
        assertThat(catalog.rows()).hasSize(82);
        assertThat(catalog.testIds()).hasSize(82).contains("TC-001", "TC-082");
    }

    private WebTestClient captchaClient() {
        CreateCaptchaChallengeUseCase create = command -> Mono.just(new CreateCaptchaChallengeResult(
                new CaptchaChallengeId(CHALLENGE_ID),
                new CaptchaImage(PNG_BASE64, "image/png"),
                300));
        VerifyCaptchaUseCase verify = command -> Mono.error(
                new AssertionError("verify use case is not part of TC-001"));
        RefreshCaptchaUseCase refresh = command -> Mono.error(
                new AssertionError("refresh use case is not part of TC-001"));
        CaptchaWebMapper mapper = new CaptchaWebMapper(
                (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code);
        return WebTestClient.bindToController(new AuthCaptchaController(create, verify, refresh, mapper)).build();
    }
}
