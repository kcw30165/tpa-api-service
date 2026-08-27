package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.*;

public record ResolvedGeneralMetadataSet(
    GeneralMetadataScope scope,
    GeneralMetadataResolutionSource resolutionSource,
    List<ResolvedGeneralMetadata> entries) {
  public ResolvedGeneralMetadataSet {
    Objects.requireNonNull(scope);
    Objects.requireNonNull(resolutionSource);
    entries = List.copyOf(Objects.requireNonNull(entries));
  }

  public static ResolvedGeneralMetadataSet normalize(
      GeneralMetadataScope scope,
      GeneralMetadataResolutionSource source,
      Collection<ResolvedGeneralMetadata> values) {
    var normalized = new LinkedHashMap<GeneralMetadataKey, ResolvedGeneralMetadata>();
    for (var value : Objects.requireNonNull(values)) {
      var previous = normalized.putIfAbsent(value.key(), value);
      if (previous != null && !previous.value().equals(value.value()))
        throw new IllegalArgumentException("Conflicting duplicate general metadata key");
    }
    return new ResolvedGeneralMetadataSet(scope, source, List.copyOf(normalized.values()));
  }

  public Optional<ResolvedGeneralMetadata> find(GeneralMetadataKey key) {
    return entries.stream().filter(e -> e.key().equals(key)).findFirst();
  }
}
