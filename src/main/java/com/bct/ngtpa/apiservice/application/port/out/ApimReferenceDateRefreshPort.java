package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.FetchReferenceDateCommand;
import java.time.LocalDate;
import reactor.core.publisher.Mono;

public interface ApimReferenceDateRefreshPort {
    Mono<LocalDate> fetchReferenceDate(FetchReferenceDateCommand command);
}
