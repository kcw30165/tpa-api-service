package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.util.Map;

public final class GeneralMetadataWireValues {
  private static final Map<GeneralMetadataName, String> NAMES =
      Map.of(
          GeneralMetadataName.FUND_PRICE,
          "fund-pri",
          GeneralMetadataName.NET_ASSET_VALUE,
          "nav",
          GeneralMetadataName.UNIT_BALANCE,
          "unit",
          GeneralMetadataName.FX_RATE,
          "fx-rate");
  private static final Map<GeneralMetadataType, String> TYPES =
      Map.of(
          GeneralMetadataType.PRECISION,
          "precision",
          GeneralMetadataType.ROUNDING_METHOD,
          "round-method");

  private GeneralMetadataWireValues() {}

  public static String nameToWire(GeneralMetadataName value) {
    return NAMES.get(java.util.Objects.requireNonNull(value));
  }

  public static GeneralMetadataName nameFromWire(String value) {
    return NAMES.entrySet().stream()
        .filter(e -> e.getValue().equals(value))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown general metadata name wire value"));
  }

  public static String typeToWire(GeneralMetadataType value) {
    return TYPES.get(java.util.Objects.requireNonNull(value));
  }

  public static GeneralMetadataType typeFromWire(String value) {
    return TYPES.entrySet().stream()
        .filter(e -> e.getValue().equals(value))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown general metadata type wire value"));
  }
}
