package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReferenceDateRedisCacheAdapter implements ReferenceDateCacheUpdatePort {

    private final Optional<CachePort> cachePort;
    private final ReferenceDateProperties referenceDateProperties;

    @Override
    public Mono<Void> updateReferenceDate(ReferenceDateCacheUpdateCommand command) {
        long ttlSeconds = referenceDateProperties.getRefresh().getCacheTtlSeconds();
        if (cachePort.isEmpty() || ttlSeconds <= 0) {
            return Mono.error(new CacheException("Cache set operation failed"));
        }
        return cachePort.get().set(command.cacheKey(), command.cacheValue(), Duration.ofSeconds(ttlSeconds));
    }
}