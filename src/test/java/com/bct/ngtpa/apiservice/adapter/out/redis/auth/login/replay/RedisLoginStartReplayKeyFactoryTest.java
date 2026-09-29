package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;
import org.junit.jupiter.api.Test;

class RedisLoginStartReplayKeyFactoryTest {

  @Test
  void keyIsPrefixedAndCommitRefScoped() {
    String key =
        new RedisLoginStartReplayKeyFactory("ngtpa")
            .keyFor(new LoginStartReplayCommitRef("rpl_opaque_commit_reference"));
    assertThat(key).isEqualTo("ngtpa:auth:login:start-replay:rpl_opaque_commit_reference");
  }

  @Test
  void constructorStripsTrailingColonFromPrefix() {
    String key =
        new RedisLoginStartReplayKeyFactory("ngtpa:")
            .keyFor(new LoginStartReplayCommitRef("rpl_opaque_commit_reference"));
    assertThat(key).isEqualTo("ngtpa:auth:login:start-replay:rpl_opaque_commit_reference");
  }

  @Test
  void constructorRejectsNullOrBlankPrefix() {
    assertThatThrownBy(() -> new RedisLoginStartReplayKeyFactory(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RedisLoginStartReplayKeyFactory("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
