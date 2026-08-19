package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public final class RegistrationOtpVerifyErrorKeys {
  public static final String REGISTRATION_AUTH_FLOW_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID
          .value();
  public static final String REGISTRATION_CSRF_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.CSRF_INVALID.value();
  public static final String REGISTRATION_OTP_INVALID =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.OTP_INVALID.value();
  public static final String REGISTRATION_OTP_EXPIRED =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.OTP_EXPIRED.value();
  public static final String REGISTRATION_OTP_MAX_ATTEMPTS_REACHED =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.OTP_LOCKED.value();
  public static final String REGISTRATION_TRUST_LOOKUP_FAILED =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey
          .REGISTRATION_TRUSTLOOKUP_FAILED
          .value();
  public static final String REGISTRATION_NO_ELIGIBLE_TRUSTS =
      com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey
          .REGISTRATION_TRUSTLOOKUP_NOELIGIBLETRUSTS
          .value();
  public static final String WORKFLOW_IDEMPOTENCY_KEY_REUSED =
      com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey
          .IDEMPOTENCY_KEY_REUSED
          .value();

  private RegistrationOtpVerifyErrorKeys() {}
}
