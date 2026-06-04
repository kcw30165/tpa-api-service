package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import reactor.core.publisher.Mono;

public interface ApimUpdatePersonalInformationPort {
    Mono<UpdatePersonalInformationResult> updateMemberInfo(UpdateMemberInfoCommand command);
}
