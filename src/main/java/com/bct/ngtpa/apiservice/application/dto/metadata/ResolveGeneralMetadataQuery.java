package com.bct.ngtpa.apiservice.application.dto.metadata;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;
import java.util.Set;

public record ResolveGeneralMetadataQuery(
    GeneralMetadataScope scope,
    Set<GeneralMetadataKey> requiredKeys,
    ActorUserRole actorUserRole) {
  public ResolveGeneralMetadataQuery {
    Objects.requireNonNull(scope, "scope must not be null");
    requiredKeys =
        Set.copyOf(Objects.requireNonNull(requiredKeys, "requiredKeys must not be null"));
    if (requiredKeys.isEmpty()) {
      throw new IllegalArgumentException("requiredKeys must not be empty");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
