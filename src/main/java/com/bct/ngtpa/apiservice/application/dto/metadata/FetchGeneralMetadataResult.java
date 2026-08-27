package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.*;

public record FetchGeneralMetadataResult(
    GeneralMetadataResolutionSource resolutionSource, List<ResolvedGeneralMetadata> entries) {
  public FetchGeneralMetadataResult {
    Objects.requireNonNull(resolutionSource);
    entries = List.copyOf(Objects.requireNonNull(entries));
  }
}
