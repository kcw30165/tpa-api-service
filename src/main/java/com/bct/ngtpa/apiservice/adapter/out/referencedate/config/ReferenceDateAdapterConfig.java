package com.bct.ngtpa.apiservice.adapter.out.referencedate.config;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.OrchestratedReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

/**
 * Spring composition configuration for the {@link ReferenceDatePort} outbound adapter.
 *
 * <p>Registers {@link OrchestratedReferenceDateAdapter} as the application-facing
 * {@link ReferenceDatePort} bean, wiring:
 * <ul>
 *   <li>{@link ReferenceDateProperties} — override-date, override-zone-id, cache TTL.</li>
 *   <li>{@code Optional<CachePort>} — present when Redis is enabled; empty when disabled.</li>
 *   <li>{@link ConfigServicePort} — Config Service adapter for dynamic reference dates.</li>
 *   <li>{@code redis-cache.key-prefix} — Redis key namespace; defaults to {@code ngtpa}.</li>
 *   <li>{@link Clock#systemDefaultZone()} — injected for deterministic testing.</li>
 * </ul>
 *
 * <p>This class is intentionally free of business logic. The orchestration behaviour
 * (source precedence, fallback chain, Redis populate) lives entirely in the adapter.
 */
@Configuration(proxyBeanMethods = false)
public class ReferenceDateAdapterConfig {

    /**
     * Produces the {@link ReferenceDatePort} bean backed by the full source chain:
     * override-date → Redis → Config Service → system date.
     *
     * @param properties        bound from {@code reference-date.*} YAML
     * @param cachePort         present when {@code redis-cache.enabled=true} (default); empty otherwise
     * @param configServicePort Config Service outbound port (always present)
     * @param keyPrefix         bound from {@code redis-cache.key-prefix}; defaults to {@code ngtpa}
     */
    @Bean
    public ReferenceDatePort referenceDatePort(
            ReferenceDateProperties properties,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort,
            @Value("${redis-cache.key-prefix:ngtpa}") String keyPrefix) {
        return new OrchestratedReferenceDateAdapter(
                properties, cachePort, keyPrefix, configServicePort, Clock.systemDefaultZone());
    }
}
