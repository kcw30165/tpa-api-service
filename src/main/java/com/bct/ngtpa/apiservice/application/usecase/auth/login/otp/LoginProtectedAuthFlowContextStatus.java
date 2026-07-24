package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;
public enum LoginProtectedAuthFlowContextStatus { VALID, MISSING_SESSION, EXPIRED_OR_TERMINAL_SESSION, WRONG_SCENARIO, INVALID_STATUS, MISSING_CONTEXT, CONTEXT_DECRYPTION_FAILED, CSRF_INVALID, ORIGIN_REFERER_INVALID }
