package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface GeneralMetadataCachePort {
  Mono<Optional<ResolvedGeneralMetadataSet>> read(GeneralMetadataScope scope);

  Mono<Void> write(ResolvedGeneralMetadataSet metadata);

  Mono<Void> evict(GeneralMetadataScope scope);

  Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
      GeneralMetadataScope scope,
      ResolvedGeneralMetadataSet expected,
      ResolvedGeneralMetadataSet replacement);
}
