package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import reactor.core.publisher.Mono;

class ReactiveRedisForgotPasswordIdempotencyOperations implements RedisForgotPasswordIdempotencyOperations {
    private final ReactiveStringRedisTemplate redisTemplate;

    ReactiveRedisForgotPasswordIdempotencyOperations(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl).map(Boolean.TRUE::equals);
    }

    @Override
    public Mono<Void> set(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl).then();
    }

    @Override
    public Mono<Optional<String>> get(String key) {
        return redisTemplate.opsForValue().get(key).map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<String> attachPendingCommitRef(String key,String action,String requestHash,String ref,Duration ttl) {
        String lua="local r=redis.call('GET',KEYS[1]); if not r then return redis.error_reply('MISSING') end; local o=cjson.decode(r); if o.status~='PROCESSING' or o.action~=ARGV[1] or o.requestHash~=ARGV[2] then return redis.error_reply('INVALID') end; if o.pendingOpaqueCommitRef and o.pendingOpaqueCommitRef~=cjson.null and o.pendingOpaqueCommitRef~='' then return r end; o.pendingOpaqueCommitRef=ARGV[3]; local n=cjson.encode(o); redis.call('SET',KEYS[1],n,'PX',ARGV[4]); return n";
        return redisTemplate.execute(RedisScript.of(lua,String.class),List.of(key),List.of(action,requestHash,ref,String.valueOf(ttl.toMillis()))).next();
    }

    @Override
    public Mono<Void> delete(String key) {
        return redisTemplate.delete(key).then();
    }
}