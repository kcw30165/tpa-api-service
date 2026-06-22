package com.bct.ngtpa.apiservice.adapter.out.referencedate.config;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.NonpReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.util.Optional;

/**
 * Spring composition configuration for the {@link ReferenceDatePort} outbound adapter.
 *
 * <p>Registers {@link NonpReferenceDateAdapter} as the application-facing
 * {@link ReferenceDatePort} bean, wiring:
 * <ul>
 *   <li>{@link ReferenceDateProperties} — accountEnv, read-path cache TTL, refresh settings.</li>
 *   <li>{@code Optional<CachePort>} — present when Redis is enabled; empty when disabled.</li>
 *   <li>{@link ConfigServicePort} — Config Service reads for reference-date lookup.</li>
 *   <li>{@link ApimReferenceDateRefreshPort} — APIM fetch used as the final non-prod upstream source.</li>
 *   <li>{@link ReferenceDateConfigPort} and {@link ReferenceDateCacheUpdatePort} — refresh-style writes after an APIM hit.</li>
 *   <li>{@link Environment} — runtime deployment environment derived from active Spring profiles.</li>
 *   <li>{@code redis-cache.key-prefix} — Redis key namespace; defaults to {@code ngtpa}.</li>
 *   <li>{@link Clock#systemDefaultZone()} — injected for deterministic testing.</li>
 * </ul>
 *
 * <p>This class is intentionally free of business logic. The orchestration behaviour
 * (deployment-env branching, source precedence, fallback chain, writes) lives entirely in the adapter.
 */
@Configuration(proxyBeanMethods = false)
public class ReferenceDateAdapterConfig {

    /**
     * Produces the {@link ReferenceDatePort} bean backed by the full source chain:
     * PROD/DR => Hong Kong system date only;
     * non-production-like => Redis → Config Service → APIM → Hong Kong system date.
     *
     * @param properties        bound from {@code reference-date.*} YAML
     * @param cachePort         present when {@code redis-cache.enabled=true} (default); empty otherwise
     * @param configServicePort Config Service outbound port (always present)
     * @param apimReferenceDateRefreshPort APIM reference-date refresh/fetch port
     * @param referenceDateConfigPort Config Service upsert port reused from refresh flow
     * @param referenceDateCacheUpdatePort Redis update port reused from refresh flow
     * @param environment       active Spring profiles used as deployment environment signal
     * @param keyPrefix         bound from {@code redis-cache.key-prefix}; defaults to {@code ngtpa}
     */
    @Bean
    public ReferenceDatePort referenceDatePort(
            ReferenceDateProperties properties,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort,
            ApimReferenceDateRefreshPort apimReferenceDateRefreshPort,
            ReferenceDateConfigPort referenceDateConfigPort,
            ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort,
            Environment environment,
            @Value("${redis-cache.key-prefix:ngtpa}") String keyPrefix) {
        return new NonpReferenceDateAdapter(
                properties,
                cachePort,
                keyPrefix,
                configServicePort,
                apimReferenceDateRefreshPort,
                referenceDateConfigPort,
                referenceDateCacheUpdatePort,
                environment,
                Clock.systemDefaultZone());
    }
}
