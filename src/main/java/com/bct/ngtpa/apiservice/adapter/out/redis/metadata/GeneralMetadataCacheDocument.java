package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import java.util.List;

record GeneralMetadataCacheDocument(
    String accountEnv,
    String trustCode,
    String policyNo,
    GeneralMetadataResolutionSource resolutionSource,
    List<ResolvedGeneralMetadata> entries) {
  static GeneralMetadataCacheDocument from(ResolvedGeneralMetadataSet value) {
    return new GeneralMetadataCacheDocument(
        value.scope().accountEnv(),
        value.scope().trustCode(),
        value.scope().policyNo(),
        value.resolutionSource(),
        value.entries());
  }

  ResolvedGeneralMetadataSet toDomain() {
    return new ResolvedGeneralMetadataSet(
        new GeneralMetadataScope(accountEnv, trustCode, policyNo), resolutionSource, entries);
  }
}
