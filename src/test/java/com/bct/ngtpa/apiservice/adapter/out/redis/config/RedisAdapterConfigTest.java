package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisStringCacheAdapter;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RedisAdapterConfigTest {

    // ── Template serialization ─────────────────────────────────────────────────

    @Test
    void reactiveRedisTemplateKeySerializerIsPlainUtf8String() {
        var config = configWithSslDisabled();
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();
        ReactiveRedisTemplate<String, String> template = config.reactiveRedisTemplate(factory);

        var ctx = template.getSerializationContext();
        ByteBuffer keyBuf = ctx.getKeySerializationPair().getWriter().write("my-cache-key");

        assertEquals("my-cache-key", StandardCharsets.UTF_8.decode(keyBuf).toString());
    }

    @Test
    void reactiveRedisTemplateValueSerializerIsPlainUtf8String() {
        var config = configWithSslDisabled();
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();
        ReactiveRedisTemplate<String, String> template = config.reactiveRedisTemplate(factory);

        var ctx = template.getSerializationContext();
        ByteBuffer valueBuf = ctx.getValueSerializationPair().getWriter().write("{\"k\":\"v\"}");

        assertEquals("{\"k\":\"v\"}", StandardCharsets.UTF_8.decode(valueBuf).toString());
    }

    @Test
    void reactiveRedisTemplateIsNotNull() {
        var config = configWithSslDisabled();
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();

        assertNotNull(config.reactiveRedisTemplate(factory));
    }

    // ── Connection factory ─────────────────────────────────────────────────────

    @Test
    void lettuceConnectionFactoryIsNotNull() {
        var config = configWithSslDisabled();

        assertNotNull(config.lettuceConnectionFactory());
    }

    @Test
    void lettuceConnectionFactoryIsLettuceType() {
        var config = configWithSslDisabled();

        assertInstanceOf(LettuceConnectionFactory.class, config.lettuceConnectionFactory());
    }

    // ── SSL disabled path does not consult SslBundles ─────────────────────────

    @Test
    void sslBundlesIsNotConsultedWhenSslDisabled() {
        SslBundles sslBundles = mock(SslBundles.class);
        var props = new RedisCacheProperties();
        props.getSsl().setEnabled(false);

        var config = new RedisAdapterConfig(props, sslBundles);
        config.lettuceConnectionFactory();

        verifyNoInteractions(sslBundles);
    }

    // ── Timeout configuration ─────────────────────────────────────────────────

    @Test
    void commandTimeoutReflectsConfiguredMilliseconds() {
        var props = new RedisCacheProperties();
        props.getSsl().setEnabled(false);
        props.setTimeoutMilliseconds(2500);
        SslBundles sslBundles = mock(SslBundles.class);

        RedisAdapterConfig config = new RedisAdapterConfig(props, sslBundles);
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();

        // LettuceConnectionFactory exposes the client configuration
        assertEquals(Duration.ofMillis(2500), factory.getClientConfiguration().getCommandTimeout());
    }

    // ── Sentinel configuration ─────────────────────────────────────────────────

    @Test
    void factoryIsCreatedWhenSentinelNodesAreProvided() {
        var props = new RedisCacheProperties();
        props.getSsl().setEnabled(false);
        props.getSentinel().setMaster("prodMaster");
        props.getSentinel().setNodes(List.of("sentinel-host:26379"));

        RedisAdapterConfig config = new RedisAdapterConfig(props, mock(SslBundles.class));

        // Factory should be created successfully (no connection attempted at creation time)
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();
        assertNotNull(factory);

    }

    // ── Hash serializers ────────────────────────────────────────────────────────

    @Test
    void hashKeySerializerIsPlainUtf8String() {
        var config = configWithSslDisabled();
        ReactiveRedisTemplate<String, String> template =
                config.reactiveRedisTemplate(config.lettuceConnectionFactory());

        ByteBuffer buf = template.getSerializationContext()
                .getHashKeySerializationPair().getWriter().write("hash-field");

        assertEquals("hash-field", StandardCharsets.UTF_8.decode(buf).toString());
    }

    @Test
    void hashValueSerializerIsPlainUtf8String() {
        var config = configWithSslDisabled();
        ReactiveRedisTemplate<String, String> template =
                config.reactiveRedisTemplate(config.lettuceConnectionFactory());

        ByteBuffer buf = template.getSerializationContext()
                .getHashValueSerializationPair().getWriter().write("{\"field\":\"v\"}");

        assertEquals("{\"field\":\"v\"}", StandardCharsets.UTF_8.decode(buf).toString());
    }

    // ── Key factory bean ──────────────────────────────────────────────────────

    @Test
    void redisCacheKeyFactoryBeanUsesConfiguredKeyPrefix() {
        var props = new RedisCacheProperties();
        props.getSsl().setEnabled(false);
        props.setKeyPrefix("testapp");

        RedisAdapterConfig config = new RedisAdapterConfig(props, mock(SslBundles.class));
        var factory = config.redisCacheKeyFactory();

        assertEquals("testapp:capability:part", factory.buildKey("capability", "part"));
    }

    // ── Cache adapter bean ────────────────────────────────────────────────────

    @Test
    void redisStringCacheAdapterBeanImplementsCachePort() {
        var config = configWithSslDisabled();
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();
        ReactiveRedisTemplate<String, String> template = config.reactiveRedisTemplate(factory);

        CachePort adapter = config.redisStringCacheAdapter(template);

        assertInstanceOf(RedisStringCacheAdapter.class, adapter);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RedisAdapterConfig configWithSslDisabled() {
        var props = new RedisCacheProperties();
        props.getSsl().setEnabled(false);
        SslBundles sslBundles = mock(SslBundles.class);
        return new RedisAdapterConfig(props, sslBundles);
    }
}
