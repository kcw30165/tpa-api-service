package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import reactor.core.publisher.Mono;

public interface ApimMemberInfoPort {
    Mono<MemberInfoResult> fetchMemberInfo(FetchMemberInfoCommand command);
}
