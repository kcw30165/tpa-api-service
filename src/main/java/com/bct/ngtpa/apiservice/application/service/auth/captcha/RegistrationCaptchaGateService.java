package com.bct.ngtpa.apiservice.application.service.auth.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaConsumeOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class RegistrationCaptchaGateService {

    private final CaptchaChallengePort captchaChallengePort;
    private final Clock clock;

    public RegistrationCaptchaGateService(CaptchaChallengePort captchaChallengePort, Clock clock) {
        this.captchaChallengePort = Objects.requireNonNull(captchaChallengePort,
                "captchaChallengePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Mono<ConsumeCaptchaChallengeResult> consume(ConsumeCaptchaChallengeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        return captchaChallengePort.load(command.captchaChallengeId())
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return Mono.just(result(command, CaptchaConsumeOutcome.NOT_FOUND));
                    }
                    CaptchaChallenge challenge = optional.get();
                    if (!challenge.expiresAt().isAfter(now) || challenge.status() == CaptchaStatus.EXPIRED) {
                        return Mono.just(result(command, CaptchaConsumeOutcome.EXPIRED));
                    }
                    return switch (challenge.status()) {
                        case CREATED -> Mono.just(result(command, CaptchaConsumeOutcome.NOT_VERIFIED));
                        case VERIFIED ->
                            captchaChallengePort.markConsumed(command.captchaChallengeId(), command.consumedAt())
                                    .thenReturn(result(command, CaptchaConsumeOutcome.CONSUMED));
                        case CONSUMED -> Mono.just(result(command, CaptchaConsumeOutcome.ALREADY_CONSUMED));
                        case REFRESHED -> Mono.just(result(command, CaptchaConsumeOutcome.REFRESHED));
                        case LOCKED -> Mono.just(result(command, CaptchaConsumeOutcome.LOCKED));
                        case EXPIRED -> Mono.just(result(command, CaptchaConsumeOutcome.EXPIRED));
                    };
                });
    }

    private ConsumeCaptchaChallengeResult result(ConsumeCaptchaChallengeCommand command,
            CaptchaConsumeOutcome outcome) {
        return new ConsumeCaptchaChallengeResult(outcome, command.captchaChallengeId());
    }
}