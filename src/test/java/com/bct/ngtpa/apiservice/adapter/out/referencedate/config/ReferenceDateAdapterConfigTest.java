package com.bct.ngtpa.apiservice.adapter.out.referencedate.config;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.NonpReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import org.junit.jupiter.api.Test;
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

    @Test
    void referenceDatePort_redisDisabled_createsNonpReferenceDateAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var configServicePort = mock(ConfigServicePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var configPort = mock(ReferenceDateConfigPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props,
                Optional.empty(),
                configServicePort,
                apimPort,
                configPort,
                cacheUpdatePort,
                new MockEnvironment(),
                "ngtpa");

        assertThat(bean).isInstanceOf(NonpReferenceDateAdapter.class);
    }

    @Test
    void referenceDatePort_redisEnabled_createsNonpReferenceDateAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var configServicePort = mock(ConfigServicePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var configPort = mock(ReferenceDateConfigPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);
        var cachePort = mock(CachePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props,
                Optional.of(cachePort),
                configServicePort,
                apimPort,
                configPort,
                cacheUpdatePort,
                new MockEnvironment(),
                "ngtpa");

        assertThat(bean).isInstanceOf(NonpReferenceDateAdapter.class);
    }

    @Test
    void referenceDatePort_customKeyPrefix_propagatedToAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var configServicePort = mock(ConfigServicePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var configPort = mock(ReferenceDateConfigPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props,
                Optional.empty(),
                configServicePort,
                apimPort,
                configPort,
                cacheUpdatePort,
                new MockEnvironment(),
                "custom-pfx");

        assertThat(bean).isInstanceOf(NonpReferenceDateAdapter.class);
    }
}
