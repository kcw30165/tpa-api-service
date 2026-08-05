package com.bct.ngtpa.apiservice.adapter.out.redis.auth;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;import java.util.List;import java.util.Objects;import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;
public final class RedisAuthFlowSessionMutationLockAdapter implements AuthFlowSessionMutationLockPort {
 private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
 private final ReactiveStringRedisTemplate redis; private final RedisCacheKeyFactory keys;
 public RedisAuthFlowSessionMutationLockAdapter(ReactiveStringRedisTemplate redis, RedisCacheKeyFactory keys){this.redis=Objects.requireNonNull(redis);this.keys=Objects.requireNonNull(keys);}
 public Mono<SessionLockHandle> acquire(AuthFlowSessionId id, Duration lease){
  Objects.requireNonNull(id,"sessionId must not be null");if(lease==null||lease.isZero()||lease.isNegative())return Mono.error(new IllegalArgumentException("leaseDuration must be positive"));
  String key=keys.key("auth","mutation-lock",id.value());String token=UUID.randomUUID().toString();
  return redis.opsForValue().setIfAbsent(key,token,lease).map(Boolean.TRUE::equals).map(ok->ok?new SessionLockHandle(true,key,token):SessionLockHandle.notAcquired()).onErrorMap(e->new CacheException("Auth flow mutation lock operation failed",e));
 }
 public Mono<Boolean> release(SessionLockHandle handle){Objects.requireNonNull(handle,"handle must not be null");if(!handle.acquired())return Mono.just(false);return redis.execute(RELEASE,List.of(handle.lockKey()),handle.ownershipToken()).next().map(v->v!=null&&v>0).defaultIfEmpty(false).onErrorMap(e->new CacheException("Auth flow mutation lock operation failed",e));}
}
