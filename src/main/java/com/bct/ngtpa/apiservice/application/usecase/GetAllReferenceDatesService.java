package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import com.bct.ngtpa.apiservice.application.dto.GetAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateEntryResult;
import com.bct.ngtpa.apiservice.application.port.in.GetAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetAllReferenceDatesService implements GetAllReferenceDatesUseCase {
    private final CacheAdminPort cacheAdminPort;

    @Override
    public Mono<GetAllReferenceDatesResult> execute() {
        return cacheAdminPort.findAllByCapability(CacheCapability.REFERENCE_DATE)
                .filter(entry -> StringUtils.hasText(entry.qualifier()))
                .map(entry -> new ReferenceDateEntryResult(entry.qualifier(), entry.value()))
                .sort(Comparator.comparing(ReferenceDateEntryResult::accountEnv))
                .collectList()
                .map(GetAllReferenceDatesResult::new);
    }
}
