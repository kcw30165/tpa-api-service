package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.ProtectedLoginStartReplay;
import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Protected login-start replay storage addressed only by an opaque commitment. */
public interface LoginStartReplayPort {
  Mono<Void> save(
      LoginStartReplayCommitRef commitRef, ProtectedLoginStartReplay replay, Duration ttl);

  Mono<Optional<ProtectedLoginStartReplay>> load(LoginStartReplayCommitRef commitRef);

  Mono<Boolean> delete(LoginStartReplayCommitRef commitRef);
}
