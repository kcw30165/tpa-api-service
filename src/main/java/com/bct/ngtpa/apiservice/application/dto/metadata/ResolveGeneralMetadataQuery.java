package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.*;

public record ResolveGeneralMetadataQuery(
    GeneralMetadataScope scope, Set<GeneralMetadataKey> requiredKeys) {
  public ResolveGeneralMetadataQuery {
    Objects.requireNonNull(scope);
    requiredKeys = Set.copyOf(Objects.requireNonNull(requiredKeys));
    if (requiredKeys.isEmpty())
      throw new IllegalArgumentException("requiredKeys must not be empty");
  }
}
