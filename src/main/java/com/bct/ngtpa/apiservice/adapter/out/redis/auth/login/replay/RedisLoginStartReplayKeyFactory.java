package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;

public final class RedisLoginStartReplayKeyFactory {
  private final String prefix;

  public RedisLoginStartReplayKeyFactory(String prefix) {
    if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("prefix required");
    this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }

  public String keyFor(LoginStartReplayCommitRef ref) {
    return prefix + ":auth:login:start-replay:" + ref.value();
  }
}
