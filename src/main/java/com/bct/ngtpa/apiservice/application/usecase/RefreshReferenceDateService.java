package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class RefreshReferenceDateService implements RefreshReferenceDateUseCase {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String CACHE_KEY_SEGMENT = ":reference-date:";
  private final ApimReferenceDateRefreshPort apimReferenceDateRefreshPort;
  private final ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort;
  private final String redisKeyPrefix;

  @Override
  public Mono<RefreshReferenceDateResult> execute(RefreshReferenceDateCommand command) {
    var fetch = new FetchReferenceDateCommand(command.accountEnv(), command.actorUserRole());
    return apimReferenceDateRefreshPort
        .fetchReferenceDate(fetch)
        .flatMap(date -> updateRedis(command.accountEnv(), date.format(DATE_FORMATTER)));
  }

  private Mono<RefreshReferenceDateResult> updateRedis(String accountEnv, String value) {
    var cacheCommand =
        new ReferenceDateCacheUpdateCommand(redisKeyPrefix + CACHE_KEY_SEGMENT + accountEnv, value);
    return referenceDateCacheUpdatePort
        .updateReferenceDate(cacheCommand)
        .thenReturn(new RefreshReferenceDateResult(accountEnv, value, true))
        .onErrorResume(
            CacheException.class,
            ex -> {
              log.warn("reference-date refresh: Redis update failed; returning partial success");
              return Mono.just(new RefreshReferenceDateResult(accountEnv, value, false));
            });
  }
}
