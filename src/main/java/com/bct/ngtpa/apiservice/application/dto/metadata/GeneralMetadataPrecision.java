package com.bct.ngtpa.apiservice.application.dto.metadata;

import java.math.BigInteger;

public record GeneralMetadataPrecision(BigInteger value) {
  public GeneralMetadataPrecision {
    if (value == null || value.signum() < 0)
      throw new IllegalArgumentException("Invalid general metadata precision");
  }

  public static GeneralMetadataPrecision parse(String raw) {
    try {
      if (raw == null || !raw.matches("[0-9]+")) throw new NumberFormatException();
      return new GeneralMetadataPrecision(new BigInteger(raw));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid general metadata precision");
    }
  }
}
