package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import reactor.core.publisher.Mono;

public interface RefreshReferenceDateUseCase {

    Mono<RefreshReferenceDateResult> execute(RefreshReferenceDateCommand command);
}