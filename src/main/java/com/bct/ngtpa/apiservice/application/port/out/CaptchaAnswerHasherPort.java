package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;

public interface CaptchaAnswerHasherPort {

    CaptchaAnswerHash hash(String answer, boolean caseSensitive);

    boolean matches(String submittedAnswer, CaptchaAnswerHash expectedHash, boolean caseSensitive);
}