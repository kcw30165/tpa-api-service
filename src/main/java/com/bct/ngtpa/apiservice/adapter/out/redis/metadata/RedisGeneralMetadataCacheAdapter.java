package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(GeneralMetadataCacheProperties.class)
public final class RedisGeneralMetadataCacheAdapter implements GeneralMetadataCachePort {
  private static final String CAPABILITY = "general-metadata";
  private final CachePort cache;
  private final RedisCacheKeyFactory keys;
  private final ObjectMapper mapper;
  private final GeneralMetadataCacheProperties properties;
  private final GeneralMetadataRedisOperations operations;

  public RedisGeneralMetadataCacheAdapter(
      CachePort cache,
      RedisCacheKeyFactory keys,
      ObjectMapper mapper,
      GeneralMetadataCacheProperties properties,
      GeneralMetadataRedisOperations operations) {
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.operations = Objects.requireNonNull(operations, "operations must not be null");
  }
  public Mono<Optional<ResolvedGeneralMetadataSet>> read(GeneralMetadataScope scope) {
    Objects.requireNonNull(scope);
    String key=key(scope);
    return cache.get(key).flatMap(value -> value.map(json -> decodeOrEvict(key,json)).orElseGet(() -> Mono.just(Optional.empty())));
  }
  public Mono<Void> write(ResolvedGeneralMetadataSet metadata) {
    Objects.requireNonNull(metadata);
    return cache.set(key(metadata.scope()), encode(GeneralMetadataCacheDocument.from(metadata)), properties.ttl());
  }
  public Mono<Void> evict(GeneralMetadataScope scope) { Objects.requireNonNull(scope); return cache.evict(key(scope)).then(); }

  private Mono<Optional<ResolvedGeneralMetadataSet>> decodeOrEvict(String key,String json) {
    try { return Mono.just(Optional.of(mapper.readValue(json, GeneralMetadataCacheDocument.class).toDomain())); }
    catch (RuntimeException | JsonProcessingException exception) { return cache.evict(key).thenReturn(Optional.empty()); }
  }
  private String encode(GeneralMetadataCacheDocument document) {
    try { return mapper.writeValueAsString(document); }
    catch (JsonProcessingException exception) { throw new CacheException("General metadata cache serialization failed", exception); }
  }
  @Override
  public Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
      GeneralMetadataScope scope,
      ResolvedGeneralMetadataSet expected,
      ResolvedGeneralMetadataSet replacement) {
    Objects.requireNonNull(scope, "scope must not be null");
    Objects.requireNonNull(expected, "expected must not be null");
    Objects.requireNonNull(replacement, "replacement must not be null");
    if (!scope.equals(expected.scope()) || !scope.equals(replacement.scope())) {
      return Mono.error(new IllegalArgumentException("Metadata scopes must match"));
    }
    return Mono.fromCallable(() -> new String[] {
          encode(GeneralMetadataCacheDocument.from(expected)),
          encode(GeneralMetadataCacheDocument.from(replacement))
        })
        .flatMap(values -> operations.replaceIfUnchanged(key(scope), values[0], values[1]));
  }

  private String key(GeneralMetadataScope scope) { return keys.key(CAPABILITY, scope.accountEnv(), digest(scope.trustCode()+"\u0000"+scope.policyNo())); }
  private String digest(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
  }
}
