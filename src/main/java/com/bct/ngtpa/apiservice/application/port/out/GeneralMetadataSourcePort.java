package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import reactor.core.publisher.Mono;

public interface GeneralMetadataSourcePort {
  Mono<FetchGeneralMetadataResult> fetch(FetchGeneralMetadataCommand command);
}
