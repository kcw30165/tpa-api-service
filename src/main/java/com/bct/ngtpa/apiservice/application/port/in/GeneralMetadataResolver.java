package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import reactor.core.publisher.Mono;

public interface GeneralMetadataResolver {
  Mono<ResolvedGeneralMetadataSet> resolve(ResolveGeneralMetadataQuery query);
}
