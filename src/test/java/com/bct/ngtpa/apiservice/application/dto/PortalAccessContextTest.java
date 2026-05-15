package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PortalAccessContextTest {

    @Test
    void actorContextPreservesSuppliedValues() {
        var actor = new ActorContext("user-01", "STAFF", "RM");

        assertEquals("user-01", actor.actorUserId());
        assertEquals("STAFF", actor.actorUserType());
        assertEquals("RM", actor.actorUserRole());
    }

    @Test
    void memberOwnerContextPreservesSuppliedValues() {
        var memberOwner = new MemberOwnerContext("member-99", "MBR");

        assertEquals("member-99", memberOwner.memberUserId());
        assertEquals("MBR", memberOwner.memberType());
    }

    @Test
    void accountContextPreservesSuppliedValues() {
        var account = new AccountContext(
                "ACC-REF-01", "JP", "POL-001", "CERT-001", "JPM", "OE");

        assertEquals("ACC-REF-01", account.accountRef());
        assertEquals("JP",         account.accountEnv());
        assertEquals("POL-001",    account.policyNo());
        assertEquals("CERT-001",   account.certNo());
        assertEquals("JPM",        account.trustCode());
        assertEquals("OE",         account.schemeType());
    }

    @Test
    void portalAccessContextComposesAllThreeComponents() {
        var actor       = new ActorContext("u1", "STAFF", "RM");
        var memberOwner = new MemberOwnerContext("m1", "MBR");
        var account     = new AccountContext("ref", "JP", "pol", "cert", "trust", "scheme");

        var ctx = new PortalAccessContext(actor, memberOwner, account);

        assertSame(actor,       ctx.actor());
        assertSame(memberOwner, ctx.memberOwner());
        assertSame(account,     ctx.account());
    }

    @Test
    void portalAccessContextRecordEquality() {
        var a = new PortalAccessContext(
                new ActorContext("u1", "STAFF", "RM"),
                new MemberOwnerContext("m1", "MBR"),
                new AccountContext("ref", "JP", "pol", "cert", "trust", "scheme"));

        var b = new PortalAccessContext(
                new ActorContext("u1", "STAFF", "RM"),
                new MemberOwnerContext("m1", "MBR"),
                new AccountContext("ref", "JP", "pol", "cert", "trust", "scheme"));

        assertEquals(a, b);
    }
}
