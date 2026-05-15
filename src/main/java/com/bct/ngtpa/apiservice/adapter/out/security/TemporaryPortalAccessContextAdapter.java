package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.MemberContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TemporaryPortalAccessContextAdapter implements PortalAccessContextPort {

    private final TemporaryPortalAccessContextProperties properties;

    @Override
    public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef) {
        var profile = properties.getProfiles().get(accountRef);
        if (profile == null) {
            return Mono.error(new MemberContextResolutionException(
                    "No temporary portal access context profile configured for accountRef: " + accountRef));
        }
        return Mono.just(new PortalAccessContext(
                new ActorContext(
                        profile.getActorUserId(),
                        profile.getActorUserType(),
                        profile.getActorUserRole()),
                new MemberOwnerContext(
                        profile.getMemberUserId(),
                        profile.getMemberType()),
                new AccountContext(
                        accountRef,
                        profile.getAccountEnv(),
                        profile.getPolicyNo(),
                        profile.getCertNo(),
                        profile.getTrustCode(),
                        profile.getSchemeType())));
    }
}
