package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import reactor.core.publisher.Mono;

public interface ReferenceDateConfigPort {

    Mono<Void> upsertReferenceDate(ReferenceDateConfigUpsertCommand command);
}