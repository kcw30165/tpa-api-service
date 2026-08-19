package com.bct.ngtpa.apiservice.adapter.out.security.login.replay;

public final class LoginStartReplayProtectionException extends RuntimeException {
  public LoginStartReplayProtectionException(String message) {
    super(message);
  }

  public LoginStartReplayProtectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
