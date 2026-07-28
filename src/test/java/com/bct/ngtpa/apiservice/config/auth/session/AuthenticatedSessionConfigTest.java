package com.bct.ngtpa.apiservice.config.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AuthenticatedSessionConfigTest {
    @Test
    void propertiesRequireDedicatedVersioned256BitKey() {
        var properties = new AuthenticatedSessionProtectionProperties();
        properties.setKeyVersion("v1");
        properties.setBase64Key(Base64.getEncoder().encodeToString(new byte[32]));
        properties.validate();
        assertThat(properties.getKeyVersion()).isEqualTo("v1");

        properties.setBase64Key(Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void compositionBuildsPortAndProtectorWithoutSecondRedisClient() {
        var properties = new AuthenticatedSessionProtectionProperties();
        properties.setKeyVersion("v1");
        properties.setBase64Key(Base64.getEncoder().encodeToString(new byte[32]));
        var config = new AuthenticatedSessionConfig();
        CachePort cache = new NoOpCachePort();

        AuthenticatedSessionProtectorPort protector =
                config.authenticatedSessionProtector(properties, new ObjectMapper());
        AuthenticatedSessionPort port = config.authenticatedSessionPort(
                cache, new RedisCacheKeyFactory("ngtpa"), config.redisAuthenticatedSessionJsonMapper());

        assertThat(protector).isInstanceOf(AesGcmAuthenticatedSessionProtector.class);
        assertThat(port).isInstanceOf(RedisAuthenticatedSessionAdapter.class);
    }

    private static final class NoOpCachePort implements CachePort {
        public Mono<Void> set(String key, String value, java.time.Duration ttl) { return Mono.empty(); }
        public Mono<java.util.Optional<String>> get(String key) { return Mono.just(java.util.Optional.empty()); }
        public Mono<Boolean> evict(String key) { return Mono.just(false); }
    }
}
