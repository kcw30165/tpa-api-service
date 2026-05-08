package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.exception.MemberContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.config.TemporaryMemberContextProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TemporaryMemberContextAdapter implements MemberContextPort {

    private final TemporaryMemberContextProperties properties;

    @Override
    public Mono<MemberContext> resolveMemberContext(MemberContextPurpose purpose) {
        var key = profileKey(purpose);
        var profile = properties.getProfiles().get(key);
        if (profile == null) {
            return Mono.error(new MemberContextResolutionException(
                    "No temporary member context profile configured for purpose: " + purpose));
        }
        return Mono.just(new MemberContext(
                profile.getPolicyNo(),
                profile.getCertNo(),
                profile.getUserId(),
                profile.getTrustCode(),
                profile.getSchemeType()));
    }

    private String profileKey(MemberContextPurpose purpose) {
        return switch (purpose) {
            case NOTIFICATIONS -> "notifications";
            case CONTRIBUTIONS -> "contributions";
        };
    }
}
