package com.bct.ngtpa.apiservice.application.usecase.personalinformation;

import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationConfigCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Writes the field-access config of the selected account to the authenticated session after a
 * successful personal-information retrieval (design section 5, D-11 and D-12).
 *
 * <p>Wraps the retrieval use case without changing it. {@link #execute} only delegates. {@link
 * #executeAndCache} delegates, then stores the item-id to config-value map together with the
 * account identifiers through {@link PersonalInformationConfigCacheService}. Each GET overwrites
 * the entry of the session.
 *
 * <p>Caching never fails the retrieval: a missing session, an empty config, an entry that cannot be
 * built, or a failed write is logged with a reason and skipped. Logs carry the request id and a
 * reason only, never identifiers or config values. Not annotated with a Spring stereotype
 * (application layer stays framework-free); wired from {@code UseCaseConfig}.
 */
public final class GetPersonalInformationCachingUseCase implements GetPersonalInformationUseCase {

  private static final Logger LOG =
      LoggerFactory.getLogger(GetPersonalInformationCachingUseCase.class);

  private final GetPersonalInformationUseCase delegate;
  private final PersonalInformationConfigCacheService cache;

  public GetPersonalInformationCachingUseCase(
      GetPersonalInformationUseCase delegate, PersonalInformationConfigCacheService cache) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
  }

  @Override
  public Mono<PersonalInformationResult> execute(GetPersonalInformationCommand command) {
    return delegate.execute(command);
  }

  @Override
  public Mono<PersonalInformationResult> executeAndCache(
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return delegate
        .execute(command)
        .flatMap(
            result ->
                cacheIfPossible(result, command, sessionId, accountRef, requestId)
                    .thenReturn(result));
  }

  private Mono<Void> cacheIfPossible(
      PersonalInformationResult result,
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    if (sessionId.isEmpty()) {
      return skipped("no-session", requestId);
    }
    if (result.config().isEmpty()) {
      return skipped("no-config", requestId);
    }
    PersonalInformationConfigCacheEntry entry;
    try {
      entry =
          new PersonalInformationConfigCacheEntry(accountRef, command.policyNo(), result.config());
    } catch (RuntimeException exception) {
      LOG.warn(
          "event=personal-information.config-cache.write.skipped reason=entry-invalid exceptionType={} requestId={}",
          exception.getClass().getSimpleName(),
          requestId);
      return Mono.empty();
    }
    return cache
        .write(sessionId.get(), entry, requestId)
        .onErrorResume(
            exception -> {
              LOG.warn(
                  "event=personal-information.config-cache.write.skipped reason=failed exceptionType={} requestId={}",
                  exception.getClass().getSimpleName(),
                  requestId);
              return Mono.empty();
            });
  }

  private static Mono<Void> skipped(String reason, String requestId) {
    LOG.info(
        "event=personal-information.config-cache.write.skipped reason={} requestId={}",
        reason,
        requestId);
    return Mono.empty();
  }
}
