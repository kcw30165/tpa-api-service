package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.Objects;

public record ResolvedGeneralMetadata(GeneralMetadataKey key, String value) {
  public ResolvedGeneralMetadata {
    Objects.requireNonNull(key, "key must not be null");
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("value must not be blank");
    value = value.trim();
  }
}
