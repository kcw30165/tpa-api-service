package com.bct.ngtpa.apiservice.application.dto;

public record PortalAccessContext(
        ActorContext actor,
        AccountContext account
) {}
