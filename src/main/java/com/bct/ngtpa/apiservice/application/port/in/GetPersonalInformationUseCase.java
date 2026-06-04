package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import reactor.core.publisher.Mono;

public interface GetPersonalInformationUseCase {
    Mono<PersonalInformationResult> execute(GetPersonalInformationCommand command);
}
