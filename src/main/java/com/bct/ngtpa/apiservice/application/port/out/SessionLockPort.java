package com.bct.ngtpa.apiservice.application.port.out;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import java.time.Duration;
import reactor.core.publisher.Mono;
public interface SessionLockPort<S> {
    Mono<SessionLockHandle> acquire(S sessionId, Duration leaseDuration);
    Mono<Boolean> release(SessionLockHandle handle);
}
