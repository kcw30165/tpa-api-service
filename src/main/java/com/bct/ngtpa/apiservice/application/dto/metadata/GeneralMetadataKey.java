package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.Objects;

public record GeneralMetadataKey(GeneralMetadataName name, GeneralMetadataType type) {
  public GeneralMetadataKey {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }
}
