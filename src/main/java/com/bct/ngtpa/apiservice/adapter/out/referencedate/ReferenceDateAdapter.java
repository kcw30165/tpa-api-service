package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;

@Slf4j
public class ReferenceDateAdapter implements ReferenceDatePort {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final ZoneId HONG_KONG_ZONE = ZoneId.of("Asia/Hong_Kong");
  private static final String CACHE_KEY_SEGMENT = ":reference-date:";

  private final ReferenceDateProperties properties;
  private final Optional<CachePort> cachePort;
  private final String keyPrefix;
  private final DeploymentEnvironmentPolicy deploymentEnvironmentPolicy;
  private final Clock clock;

  public ReferenceDateAdapter(
      ReferenceDateProperties properties,
      Optional<CachePort> cachePort,
      String keyPrefix,
      Environment environment,
      Clock clock) {
    this.properties = properties;
    this.cachePort = cachePort;
    this.keyPrefix = keyPrefix;
    this.deploymentEnvironmentPolicy = new DeploymentEnvironmentPolicy(environment);
    this.clock = clock;
  }

  @Override
  public Mono<LocalDate> resolveReferenceDate(String accountEnv) {
    if (deploymentEnvironmentPolicy.isProductionLikeDeployment()) {
      return Mono.fromSupplier(this::resolveHongKongSystemDate);
    }

    return readFromRedis(accountEnv)
        .switchIfEmpty(Mono.fromSupplier(this::resolveHongKongSystemDate));
  }

  private Mono<LocalDate> readFromRedis(String accountEnv) {
    if (cachePort.isEmpty()) {
      return Mono.empty();
    }

    return Mono.defer(
        () ->
            cachePort
                .get()
                .get(cacheKey(accountEnv))
                .flatMap(
                    value ->
                        parseOptionalDate(
                            value.orElse(null),
                            () ->
                                log.warn(
                                    "reference-date: Redis cache value is not a valid date; falling through to Hong Kong system date")))
                .onErrorResume(
                    ex -> {
                      log.warn(
                          "reference-date: Redis cache read failed; falling through to Hong Kong system date");
                      return Mono.empty();
                    }));
  }

  private Mono<LocalDate> parseOptionalDate(String rawDate, Runnable invalidLogger) {
    if (rawDate == null || rawDate.isBlank()) {
      return Mono.empty();
    }

    try {
      return Mono.just(LocalDate.parse(rawDate.trim(), DATE_FORMATTER));
    } catch (DateTimeParseException ex) {
      invalidLogger.run();
      return Mono.empty();
    }
  }

  private LocalDate resolveHongKongSystemDate() {
    return clock.instant().atZone(HONG_KONG_ZONE).toLocalDate();
  }

  private String cacheKey(String accountEnv) {
    return keyPrefix + CACHE_KEY_SEGMENT + accountEnv;
  }

  private String format(LocalDate date) {
    return DATE_FORMATTER.format(date);
  }
}
