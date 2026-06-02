package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface GetPersonalInformationUseCase {
    Mono<Map<String, Object>> execute(GetPersonalInformationCommand command);
}
