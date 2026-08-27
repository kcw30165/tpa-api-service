package com.bct.ngtpa.apiservice.application.dto.metadata;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;
import java.util.Set;

public record FetchGeneralMetadataCommand(
    GeneralMetadataScope scope,
    Set<GeneralMetadataKey> requestedKeys,
    ActorUserRole actorUserRole) {
  public FetchGeneralMetadataCommand {
    Objects.requireNonNull(scope, "scope must not be null");
    requestedKeys =
        Set.copyOf(Objects.requireNonNull(requestedKeys, "requestedKeys must not be null"));
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
