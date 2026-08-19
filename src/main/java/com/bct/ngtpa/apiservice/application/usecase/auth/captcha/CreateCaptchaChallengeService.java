package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class CreateCaptchaChallengeService implements CreateCaptchaChallengeUseCase {

  private final AuthWorkflowPolicyPort authWorkflowPolicyPort;
  private final CaptchaImageGeneratorPort captchaImageGeneratorPort;
  private final CaptchaAnswerHasherPort captchaAnswerHasherPort;
  private final CaptchaChallengePort captchaChallengePort;
  private final CaptchaChallengeIdGenerator captchaChallengeIdGenerator;
  private final Clock clock;

  public CreateCaptchaChallengeService(
      AuthWorkflowPolicyPort authWorkflowPolicyPort,
      CaptchaImageGeneratorPort captchaImageGeneratorPort,
      CaptchaAnswerHasherPort captchaAnswerHasherPort,
      CaptchaChallengePort captchaChallengePort,
      CaptchaChallengeIdGenerator captchaChallengeIdGenerator,
      Clock clock) {
    this.authWorkflowPolicyPort =
        Objects.requireNonNull(authWorkflowPolicyPort, "authWorkflowPolicyPort must not be null");
    this.captchaImageGeneratorPort =
        Objects.requireNonNull(
            captchaImageGeneratorPort, "captchaImageGeneratorPort must not be null");
    this.captchaAnswerHasherPort =
        Objects.requireNonNull(captchaAnswerHasherPort, "captchaAnswerHasherPort must not be null");
    this.captchaChallengePort =
        Objects.requireNonNull(captchaChallengePort, "captchaChallengePort must not be null");
    this.captchaChallengeIdGenerator =
        Objects.requireNonNull(
            captchaChallengeIdGenerator, "captchaChallengeIdGenerator must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Mono<CreateCaptchaChallengeResult> create(CreateCaptchaChallengeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    CaptchaPolicy policy = captchaPolicy();
    return captchaImageGeneratorPort
        .generate(policy)
        .flatMap(
            generatedCaptcha -> {
              Instant createdAt = clock.instant();
              var captchaChallengeId = captchaChallengeIdGenerator.nextId();
              var answerHash =
                  captchaAnswerHasherPort.hash(generatedCaptcha.answer(), policy.caseSensitive());
              var challenge =
                  new CaptchaChallenge(
                      captchaChallengeId,
                      answerHash,
                      CaptchaStatus.CREATED,
                      0,
                      policy.maxAttempts(),
                      createdAt,
                      createdAt.plusSeconds(policy.ttlSeconds()),
                      null,
                      null,
                      null,
                      null);
              return captchaChallengePort
                  .create(challenge)
                  .thenReturn(
                      new CreateCaptchaChallengeResult(
                          captchaChallengeId, generatedCaptcha.image(), policy.ttlSeconds()));
            });
  }

  private CaptchaPolicy captchaPolicy() {
    return Objects.requireNonNull(
        authWorkflowPolicyPort.registrationPolicy().captcha(),
        "registration captcha policy must not be null");
  }
}
