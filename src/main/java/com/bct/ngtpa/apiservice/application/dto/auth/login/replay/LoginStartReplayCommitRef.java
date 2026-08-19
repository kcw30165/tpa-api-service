package com.bct.ngtpa.apiservice.application.dto.auth.login.replay;

import java.util.regex.Pattern;

public record LoginStartReplayCommitRef(String value) {
  private static final Pattern SAFE = Pattern.compile("^rpl_[A-Za-z0-9_-]{20,128}$");

  public LoginStartReplayCommitRef {
    if (value == null || !SAFE.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid login replay commitment reference");
    }
  }

  @Override
  public String toString() {
    return "LoginStartReplayCommitRef[redacted]";
  }
}
