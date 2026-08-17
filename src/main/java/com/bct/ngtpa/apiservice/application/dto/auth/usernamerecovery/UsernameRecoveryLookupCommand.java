package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import java.util.Objects;

public record UsernameRecoveryLookupCommand(
        MemberIdentityType identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile,
        ActorUserRole actorUserRole) {
    public UsernameRecoveryLookupCommand {
        Objects.requireNonNull(identityType, "identityType must not be null");
        Objects.requireNonNull(identityValue, "identityValue must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
        identityValue=identityValue.trim(); dateOfBirth=dateOfBirth.trim();
        if(identityValue.isBlank()||dateOfBirth.isBlank()) throw new IllegalArgumentException("username recovery lookup command fields must not be blank");
    }
    @Override public String toString(){return "UsernameRecoveryLookupCommand[identityType="+identityType+", identityValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>, actorUserRole="+actorUserRole+"]";}
}
