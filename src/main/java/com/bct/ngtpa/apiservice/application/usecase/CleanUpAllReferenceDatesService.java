package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import com.bct.ngtpa.apiservice.application.dto.CleanUpAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.port.in.CleanUpAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CleanUpAllReferenceDatesService implements CleanUpAllReferenceDatesUseCase {
    private final CacheAdminPort cacheAdminPort;

    @Override
    public Mono<CleanUpAllReferenceDatesResult> execute() {
        return cacheAdminPort.evictAllByCapability(CacheCapability.REFERENCE_DATE)
                .map(deletedCount -> new CleanUpAllReferenceDatesResult(deletedCount == null ? 0L : deletedCount));
    }
}
