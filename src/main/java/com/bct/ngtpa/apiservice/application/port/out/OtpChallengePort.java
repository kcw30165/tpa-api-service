package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowOtpPort;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import reactor.core.publisher.Mono;

public interface OtpChallengePort extends AuthFlowOtpPort {

    Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command);

    Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand command);

    Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand command);

    Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command);
}