package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.config.ReferenceDateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ConfigBackedReferenceDateAdapter implements ReferenceDatePort {

    private final ReferenceDateProperties properties;
    private final ReferenceDateResolver referenceDateResolver;

    @Override
    public Mono<LocalDate> resolveReferenceDate() {
        return Mono.fromSupplier(this::resolve);
    }

    private LocalDate resolve() {
        return referenceDateResolver.resolve(
                properties.getDeploymentEnv(),
                properties.getOverrideDate(),
                properties.getOverrideZoneId());
    }
}