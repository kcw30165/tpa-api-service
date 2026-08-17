package com.bct.ngtpa.apiservice.application.dto;

import java.util.Objects;

public record ActorContext(
        String actorUserId,
        ActorUserRole actorUserRole
) {
    public ActorContext {
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    }
}
