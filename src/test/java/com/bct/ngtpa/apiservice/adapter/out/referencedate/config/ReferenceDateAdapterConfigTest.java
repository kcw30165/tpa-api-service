package com.bct.ngtpa.apiservice.adapter.out.referencedate.config;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.OrchestratedReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ReferenceDateAdapterConfig}.
 *
 * <p>Verifies that the configuration factory method produces an
 * {@link OrchestratedReferenceDateAdapter} under both Redis-enabled and
 * Redis-disabled scenarios. No Spring context is required.
 */
class ReferenceDateAdapterConfigTest {

    @Test
    void referenceDatePort_redisDisabled_createsOrchestratedAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var configServicePort = mock(ConfigServicePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props, Optional.empty(), configServicePort, "ngtpa");

        assertThat(bean).isInstanceOf(OrchestratedReferenceDateAdapter.class);
    }

    @Test
    void referenceDatePort_redisEnabled_createsOrchestratedAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var configServicePort = mock(ConfigServicePort.class);
        var cachePort = mock(CachePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props, Optional.of(cachePort), configServicePort, "ngtpa");

        assertThat(bean).isInstanceOf(OrchestratedReferenceDateAdapter.class);
    }

    @Test
    void referenceDatePort_customKeyPrefix_propagatedToAdapter() {
        var config = new ReferenceDateAdapterConfig();
        var props = new ReferenceDateProperties();
        var configServicePort = mock(ConfigServicePort.class);

        ReferenceDatePort bean = config.referenceDatePort(
                props, Optional.empty(), configServicePort, "custom-pfx");

        assertThat(bean).isInstanceOf(OrchestratedReferenceDateAdapter.class);
    }
}
