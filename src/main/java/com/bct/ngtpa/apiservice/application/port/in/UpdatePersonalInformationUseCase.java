package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import reactor.core.publisher.Mono;

public interface UpdatePersonalInformationUseCase {
    Mono<UpdatePersonalInformationResult> execute(UpdatePersonalInformationCommand command);
}
