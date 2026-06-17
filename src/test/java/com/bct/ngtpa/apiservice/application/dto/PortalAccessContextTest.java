package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class PortalAccessContextTest {

    @Test
    void actorContextPreservesSuppliedValues() {
        var actor = new ActorContext("user-01", "RM");

        assertEquals("user-01", actor.actorUserId());
        // assertEquals("STAFF", actor.actorUserType());
        assertEquals("RM", actor.actorUserRole());
    }


    @Test
    void accountContextPreservesSuppliedValues() {
        var account = new AccountContext(
                "ACC-REF-01", "JP", "POL-001", "CERT-001", "JPM", "OE",
                TermStatus.P, LocalDate.of(2026, 3, 31));

        assertEquals("ACC-REF-01", account.accountRef());
        assertEquals("JP",         account.accountEnv());
        assertEquals("POL-001",    account.policyNo());
        assertEquals("CERT-001",   account.certNo());
        assertEquals("JPM",        account.trustCode());
        assertEquals("OE",         account.schemeType());
        assertEquals(TermStatus.P,  account.termStatus());
        assertEquals(LocalDate.of(2026, 3, 31), account.termCompletionDate());
    }

    @Test
    void portalAccessContextComposesAllThreeComponents() {
        var actor       = new ActorContext("u1", "RM");
        var account     = new AccountContext(
            "ref", "JP", "pol", "cert", "trust", "scheme",
            TermStatus.S, LocalDate.of(2026, 4, 1));

        var ctx = new PortalAccessContext(actor, account);

        assertSame(actor,       ctx.actor());
        assertSame(account,     ctx.account());
    }

    @Test
    void portalAccessContextRecordEquality() {
        var a = new PortalAccessContext(
                new ActorContext("u1", "RM"),
            new AccountContext(
                "ref", "JP", "pol", "cert", "trust", "scheme",
                TermStatus.O, LocalDate.of(2026, 5, 1)));

        var b = new PortalAccessContext(
                new ActorContext("u1", "RM"),
            new AccountContext(
                "ref", "JP", "pol", "cert", "trust", "scheme",
                TermStatus.O, LocalDate.of(2026, 5, 1)));

        assertEquals(a, b);
    }
}
