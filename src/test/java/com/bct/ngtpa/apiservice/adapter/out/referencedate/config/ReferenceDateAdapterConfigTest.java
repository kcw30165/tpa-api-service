package com.bct.ngtpa.apiservice.adapter.out.referencedate.config;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.NonpReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ReferenceDateAdapterConfig}.
 *
 * <p>Verifies that the configuration factory method produces an
 * {@link NonpReferenceDateAdapter} under both Redis-enabled and
 * Redis-disabled scenarios. No Spring context is required.
 */
class ReferenceDateAdapterConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReferenceDateAdapterConfig.class)
            .withBean(ReferenceDateProperties.class, ReferenceDateProperties::new)
            .withBean(ApimReferenceDateRefreshPort.class, () -> mock(ApimReferenceDateRefreshPort.class))
            .withBean(ReferenceDateCacheUpdatePort.class, () -> mock(ReferenceDateCacheUpdatePort.class))
            .withBean(MockEnvironment.class, MockEnvironment::new)
            .withBean(org.springframework.core.env.Environment.class, MockEnvironment::new);

    @Test
    void referenceDatePort_redisDisabled_createsNonpReferenceDateAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props,
                Optional.empty(),
                apimPort,
                cacheUpdatePort,
                new MockEnvironment(),
                "ngtpa");

        assertThat(bean).isInstanceOf(NonpReferenceDateAdapter.class);
    }

    @Test
    void referenceDatePort_redisEnabled_createsNonpReferenceDateAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);
        var cachePort = mock(CachePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props,
                Optional.of(cachePort),
                apimPort,
                cacheUpdatePort,
                new MockEnvironment(),
                "ngtpa");

        assertThat(bean).isInstanceOf(NonpReferenceDateAdapter.class);
    }

    @Test
    void referenceDatePort_customKeyPrefix_propagatedToAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props,
                Optional.empty(),
                apimPort,
                cacheUpdatePort,
                new MockEnvironment(),
                "custom-pfx");

        assertThat(bean).isInstanceOf(NonpReferenceDateAdapter.class);
    }

    @Test
    void springContext_registersSingleReferenceDatePortBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReferenceDatePort.class);
            assertThat(context.getBean(ReferenceDatePort.class)).isInstanceOf(NonpReferenceDateAdapter.class);
        });
    }
}
