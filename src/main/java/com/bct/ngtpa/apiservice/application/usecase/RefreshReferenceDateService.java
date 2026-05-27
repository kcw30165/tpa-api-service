package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Slf4j
public class RefreshReferenceDateService implements RefreshReferenceDateUseCase {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CONFIG_KEY_PREFIX = "reference-date.";
    private static final String CACHE_KEY_SEGMENT = ":reference-date:";

    private final ApimReferenceDateRefreshPort apimReferenceDateRefreshPort;
    private final ReferenceDateConfigPort referenceDateConfigPort;
    private final ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort;
    private final String redisKeyPrefix;

    @Override
    public Mono<RefreshReferenceDateResult> execute(RefreshReferenceDateCommand command) {
        return apimReferenceDateRefreshPort.fetchReferenceDate(command.accountEnv())
        .flatMap(referenceDate -> {
            String formattedReferenceDate = referenceDate.format(DATE_FORMATTER);
            var upsertCommand = new ReferenceDateConfigUpsertCommand(
                CONFIG_KEY_PREFIX + command.accountEnv(),
                formattedReferenceDate);
            return referenceDateConfigPort.upsertReferenceDate(upsertCommand)
                    .then(Mono.defer(() -> updateRedis(command.accountEnv(), formattedReferenceDate)));
        });
    }

        private Mono<RefreshReferenceDateResult> updateRedis(String accountEnv, String formattedReferenceDate) {
        var cacheCommand = new ReferenceDateCacheUpdateCommand(
            redisKeyPrefix + CACHE_KEY_SEGMENT + accountEnv,
            formattedReferenceDate);
        return referenceDateCacheUpdatePort.updateReferenceDate(cacheCommand)
            .thenReturn(new RefreshReferenceDateResult(
                accountEnv,
                formattedReferenceDate,
                true,
                true))
            .onErrorResume(CacheException.class, ex -> {
                log.warn("reference-date refresh: Redis update failed; returning partial success");
                return Mono.just(new RefreshReferenceDateResult(
                    accountEnv,
                    formattedReferenceDate,
                    true,
                    false));
            });
        }
}