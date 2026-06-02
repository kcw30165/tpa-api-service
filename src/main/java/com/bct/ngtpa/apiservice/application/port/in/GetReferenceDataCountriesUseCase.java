package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GetReferenceDataCountriesUseCase {

    Mono<ReferenceDataCountriesResult> execute(GetReferenceDataCountriesCommand command);
}
