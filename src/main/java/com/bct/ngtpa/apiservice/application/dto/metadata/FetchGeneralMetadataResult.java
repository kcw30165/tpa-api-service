package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record FetchGeneralMetadataResult(
    Optional<GeneralMetadataResolutionSource> resolutionSource,
    List<ResolvedGeneralMetadata> entries) {
  public FetchGeneralMetadataResult {
    resolutionSource =
        Objects.requireNonNull(resolutionSource, "resolutionSource must not be null");
    entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    if (entries.isEmpty() && resolutionSource.isPresent())
      throw new IllegalArgumentException("Empty metadata must be unresolved");
    if (!entries.isEmpty() && resolutionSource.isEmpty())
      throw new IllegalArgumentException("Non-empty metadata requires resolution source");
  }
}
