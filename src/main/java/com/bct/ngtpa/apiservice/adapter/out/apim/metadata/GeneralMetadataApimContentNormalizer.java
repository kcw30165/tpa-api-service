package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRow;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import java.util.*;

public final class GeneralMetadataApimContentNormalizer {
  public FetchGeneralMetadataResult normalize(
      String requestedPolicyNo, List<GeneralMetadataApimRow> rows) {
    if (requestedPolicyNo == null || requestedPolicyNo.isBlank())
      throw new IllegalArgumentException("requestedPolicyNo must not be blank");
    var normalized = new LinkedHashMap<String, ResolvedGeneralMetadata>();
    GeneralMetadataResolutionSource source = null;
    for (var row : Objects.requireNonNull(rows, "rows must not be null")) {
      if (row == null) throw new IllegalArgumentException("General metadata row must not be null");
      var rowSource = source(row.policyNo(), requestedPolicyNo);
      if (source != null && source != rowSource)
        throw new IllegalArgumentException("Mixed general metadata policy resolution");
      source = rowSource;
      var name = GeneralMetadataWireValues.nameFromWire(row.metadataName());
      var type = GeneralMetadataWireValues.typeFromWire(row.metadataType());
      if (type == GeneralMetadataType.PRECISION)
        GeneralMetadataPrecision.parse(row.metadataValue());
      var key = new GeneralMetadataKey(name, type);
      var value = new ResolvedGeneralMetadata(key, row.metadataValue());
      var uniqueness =
          row.policyNo() + "\u0000" + row.metadataName() + "\u0000" + row.metadataType();
      var previous = normalized.putIfAbsent(uniqueness, value);
      if (previous != null && !previous.value().equals(value.value()))
        throw new IllegalArgumentException("Conflicting duplicate general metadata row");
    }
    return new FetchGeneralMetadataResult(
        Optional.ofNullable(source), List.copyOf(normalized.values()));
  }

  private GeneralMetadataResolutionSource source(String policy, String requested) {
    if ("all".equals(policy)) return GeneralMetadataResolutionSource.DEFAULT_ALL;
    if (requested.equals(policy)) return GeneralMetadataResolutionSource.POLICY_SPECIFIC;
    throw new IllegalArgumentException("Unexpected general metadata policy");
  }
}
