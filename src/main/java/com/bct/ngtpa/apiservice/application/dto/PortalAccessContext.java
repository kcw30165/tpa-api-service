package com.bct.ngtpa.apiservice.application.dto;

public record PortalAccessContext(
        ActorContext actor,
        MemberOwnerContext memberOwner,
        AccountContext account
) {}
