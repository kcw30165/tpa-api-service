package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ConfigBackedReferenceDateAdapter implements ReferenceDatePort {

    private final ReferenceDateProperties properties;
    private final ReferenceDateResolver referenceDateResolver;
    private final Environment environment;

    @Override
    public Mono<LocalDate> resolveReferenceDate() {
        return Mono.fromSupplier(this::resolve);
    }

    private LocalDate resolve() {
        return referenceDateResolver.resolve(
                properties.getAccountEnv(),
                currentDeploymentEnv(),
                properties.getOverrideDate(),
                properties.getOverrideZoneId());
    }

    private String currentDeploymentEnv() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "";
        }
        return activeProfiles[0];
    }
}