package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import reactor.core.publisher.Mono;

public interface ApimUpdatePersonalInformationPort {
    Mono<List<UpdatePersonalInformationResult>> updateMemberInfo(UpdateMemberInfoCommand command);
}
