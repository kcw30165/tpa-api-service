package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import reactor.core.publisher.Mono;

public interface MemberContextPort {
    Mono<MemberContext> resolveMemberContext(MemberContextPurpose purpose);
}
