package com.bct.ngtpa.apiservice.application.usecase.personalinformation;

import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Supplies the field-access config the PUT validates against (design D-13).
 *
 * <p>{@link #resolve} reads the config cached in the authenticated session for this account; on a
 * miss, or when there is no session cookie, it makes one TRPGetMemberInfo call through {@link
 * GetPersonalInformationUseCase#executeAndCache}, which also refreshes the cache. {@link #refresh}
 * always fetches: the PUT uses it once when a rejection was decided from a cached entry, so a stale
 * cache cannot block a member. A fetch failure is propagated unchanged to the caller.
 *
 * <p>Returns the item-id to access-value map only (no member data). An empty map is returned as is:
 * the access validator then treats every submitted bound field as hidden. Logs carry the request id
 * and a reason only. Not a Spring bean by annotation (application layer stays framework-free);
 * wired from {@code PersonalInformationAccessConfigApplicationConfig}.
 */
public final class PersonalInformationAccessConfigService {

  private static final Logger LOG =
      LoggerFactory.getLogger(PersonalInformationAccessConfigService.class);

  private final GetPersonalInformationUseCase retrieval;
  private final PersonalInformationConfigCacheService cache;

  /** The config to validate against and whether it came from the session cache. */
  public record Resolved(Map<String, String> itemConfig, boolean fromCache) {

    public Resolved {
      Objects.requireNonNull(itemConfig, "itemConfig must not be null");
      itemConfig = Collections.unmodifiableMap(new LinkedHashMap<>(itemConfig));
    }
  }

  public PersonalInformationAccessConfigService(
      GetPersonalInformationUseCase retrieval, PersonalInformationConfigCacheService cache) {
    this.retrieval = Objects.requireNonNull(retrieval, "retrieval must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
  }

  public Mono<Resolved> resolve(
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    if (sessionId.isEmpty()) {
      return fetch(command, sessionId, accountRef, requestId, "no-session")
          .map(config -> new Resolved(config, false));
    }
    return cache
        .read(sessionId.get(), accountRef, command.policyNo(), requestId)
        .flatMap(
            cached ->
                cached.isPresent()
                    ? Mono.just(new Resolved(cached.get().itemConfig(), true))
                    : fetch(command, sessionId, accountRef, requestId, "cache-miss")
                        .map(config -> new Resolved(config, false)));
  }

  public Mono<Map<String, String>> refresh(
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return fetch(command, sessionId, accountRef, requestId, "refresh");
  }

  private Mono<Map<String, String>> fetch(
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId,
      String reason) {
    LOG.info(
        "event=personal-information.access-config.fetch reason={} requestId={}", reason, requestId);
    return retrieval
        .executeAndCache(command, sessionId, accountRef, requestId)
        .map(PersonalInformationResult::config);
  }
}
