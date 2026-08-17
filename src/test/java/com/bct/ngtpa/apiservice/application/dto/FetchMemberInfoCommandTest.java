package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FetchMemberInfoCommandTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        FetchMemberInfoCommand command = new FetchMemberInfoCommand("JP", "POL", "CERT", "USER", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

        assertEquals("JP", command.getAccountEnv());
        assertEquals("POL", command.getPolicyNo());
        assertEquals("CERT", command.getCertNo());
        assertEquals("USER", command.getUserId());
    }

    @Test
    void implementsValueEqualityAndHashCode() {
        FetchMemberInfoCommand left = new FetchMemberInfoCommand("JP", "POL", "CERT", "USER", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
        FetchMemberInfoCommand same = new FetchMemberInfoCommand("JP", "POL", "CERT", "USER", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
        FetchMemberInfoCommand different = new FetchMemberInfoCommand("HK", "POL", "CERT", "USER", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

        assertEquals(left, left);
        assertEquals(left, same);
        assertEquals(left.hashCode(), same.hashCode());
        assertNotEquals(left, different);
        assertNotEquals(left, null);
        assertNotEquals(left, "not-a-command");
    }

    @Test
    void equalityHandlesNullComponents() {
        FetchMemberInfoCommand left = new FetchMemberInfoCommand(null, null, null, null, com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
        FetchMemberInfoCommand same = new FetchMemberInfoCommand(null, null, null, null, com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
        FetchMemberInfoCommand different = new FetchMemberInfoCommand(null, null, null, "USER", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

        assertTrue(left.equals(same));
        assertFalse(left.equals(different));
        assertEquals(left.hashCode(), same.hashCode());
    }
}
