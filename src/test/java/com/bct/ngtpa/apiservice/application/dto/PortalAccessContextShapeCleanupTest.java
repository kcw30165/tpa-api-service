package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PortalAccessContextShapeCleanupTest {

    @Test
    void portalAccessContextContainsOnlyActorAndAccount() {
        RecordComponent[] components = PortalAccessContext.class.getRecordComponents();

        assertEquals(2, components.length);
        assertEquals("actor", components[0].getName());
        assertEquals(ActorContext.class, components[0].getType());
        assertEquals("account", components[1].getName());
        assertEquals(AccountContext.class, components[1].getType());
    }

    @Test
    void portalAccessContextDoesNotExposeMemberOwnerAccessor() {
        assertFalse(Arrays.stream(PortalAccessContext.class.getMethods())
                .anyMatch(method -> "memberOwner".equals(method.getName())));
    }

    @Test
    void preservesActorAndAccountValues() {
        ActorContext actor = new ActorContext("actor-001", "MEMBER");
        AccountContext account = new AccountContext(
                "ACC-1",
                "JP",
                "POL-1",
                "CERT-1",
                "JPM",
                "OE",
                TermStatus.O,
                LocalDate.of(2026, 3, 31));

        PortalAccessContext context = new PortalAccessContext(actor, account);

        assertSame(actor, context.actor());
        assertSame(account, context.account());
    }
}
