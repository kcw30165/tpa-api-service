package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisCachePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    void defaultEnabledIsTrue() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertTrue(props.isEnabled());
        });
    }

    @Test
    void defaultPasswordIsEmpty() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals("", props.getPassword());
        });
    }

    @Test
    void defaultTimeoutMillisecondsIs1000() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals(1000, props.getTimeoutMilliseconds());
        });
    }

    @Test
    void defaultSentinelMasterIsNgtpaMaster() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals("ngtpaMaster", props.getSentinel().getMaster());
        });
    }

    @Test
    void defaultSentinelNodesIsEmptyList() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertTrue(props.getSentinel().getNodes().isEmpty());
        });
    }

    @Test
    void defaultSentinelPasswordIsEmpty() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals("", props.getSentinel().getPassword());
        });
    }

    @Test
    void defaultSslEnabledIsTrue() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertTrue(props.getSsl().isEnabled());
        });
    }

    @Test
    void defaultSslBundleIsRedisMtls() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals("redis-mtls", props.getSsl().getBundle());
        });
    }

    @Test
    void defaultPoolMaxActiveIs20() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals(20, props.getLettuce().getPool().getMaxActive());
        });
    }

    @Test
    void defaultPoolMaxIdleIs10() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals(10, props.getLettuce().getPool().getMaxIdle());
        });
    }

    @Test
    void defaultPoolMinIdleIs0() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals(0, props.getLettuce().getPool().getMinIdle());
        });
    }

    @Test
    void defaultPoolMaxWaitMillisecondsIs3000() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals(3000L, props.getLettuce().getPool().getMaxWaitMilliseconds());
        });
    }

    @Test
    void defaultShutdownTimeoutMillisecondsIs5000() {
        contextRunner.run(context -> {
            var props = context.getBean(RedisCacheProperties.class);
            assertEquals(5000L, props.getLettuce().getShutdownTimeoutMilliseconds());
        });
    }

    // ── YAML full-binding ─────────────────────────────────────────────────────

    @Test
    void bindsAllPropertiesFromYaml() {
        var yaml = """
                redis-cache:
                  enabled: false
                  password: secret123
                  timeout-milliseconds: 2000
                  sentinel:
                    master: myMaster
                    nodes:
                      - redis-node1.svc.cluster.local:26379
                      - redis-node2.svc.cluster.local:26379
                    password: sentinelPass
                  ssl:
                    enabled: false
                    bundle: custom-bundle
                  lettuce:
                    pool:
                      max-active: 30
                      max-idle: 15
                      min-idle: 5
                      max-wait-milliseconds: 4000
                    shutdown-timeout-milliseconds: 8000
                """;

        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withInitializer(context -> {
                    var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                    try {
                        var sources = new YamlPropertySourceLoader().load("test", resource);
                        sources.forEach(s -> context.getEnvironment().getPropertySources().addLast(s));
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Failed to load YAML", ex);
                    }
                })
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);

                    assertFalse(props.isEnabled());
                    assertEquals("secret123", props.getPassword());
                    assertEquals(2000, props.getTimeoutMilliseconds());

                    assertEquals("myMaster", props.getSentinel().getMaster());
                    assertEquals(
                            List.of("redis-node1.svc.cluster.local:26379",
                                    "redis-node2.svc.cluster.local:26379"),
                            props.getSentinel().getNodes());
                    assertEquals("sentinelPass", props.getSentinel().getPassword());

                    assertFalse(props.getSsl().isEnabled());
                    assertEquals("custom-bundle", props.getSsl().getBundle());

                    assertEquals(30, props.getLettuce().getPool().getMaxActive());
                    assertEquals(15, props.getLettuce().getPool().getMaxIdle());
                    assertEquals(5, props.getLettuce().getPool().getMinIdle());
                    assertEquals(4000L, props.getLettuce().getPool().getMaxWaitMilliseconds());
                    assertEquals(8000L, props.getLettuce().getShutdownTimeoutMilliseconds());
                });
    }

    // ── Individual property-value overrides ───────────────────────────────────

    @Test
    void canDisableViaProperty() {
        contextRunner
                .withPropertyValues("redis-cache.enabled=false")
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);
                    assertFalse(props.isEnabled());
                });
    }

    @Test
    void bindsSentinelMasterFromPropertyValue() {
        contextRunner
                .withPropertyValues("redis-cache.sentinel.master=prodMaster")
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);
                    assertEquals("prodMaster", props.getSentinel().getMaster());
                });
    }

    @Test
    void bindsSentinelNodesFromPropertyValue() {
        contextRunner
                .withPropertyValues(
                        "redis-cache.sentinel.nodes[0]=host1:26379",
                        "redis-cache.sentinel.nodes[1]=host2:26379")
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);
                    assertEquals(List.of("host1:26379", "host2:26379"), props.getSentinel().getNodes());
                });
    }

    @Test
    void bindsSslBundleFromPropertyValue() {
        contextRunner
                .withPropertyValues("redis-cache.ssl.bundle=my-tls-bundle")
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);
                    assertEquals("my-tls-bundle", props.getSsl().getBundle());
                });
    }

    @Test
    void bindsTimeoutFromPropertyValue() {
        contextRunner
                .withPropertyValues("redis-cache.timeout-milliseconds=3000")
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);
                    assertEquals(3000, props.getTimeoutMilliseconds());
                });
    }

    @Test
    void bindsPasswordFromPropertyValue() {
        contextRunner
                .withPropertyValues("redis-cache.password=myredispass",
                        "redis-cache.sentinel.password=mysentinelpass")
                .run(context -> {
                    var props = context.getBean(RedisCacheProperties.class);
                    assertEquals("myredispass", props.getPassword());
                    assertEquals("mysentinelpass", props.getSentinel().getPassword());
                });
    }

    // ── Minimal test config ───────────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RedisCacheProperties.class)
    static class TestConfig {
    }
}
