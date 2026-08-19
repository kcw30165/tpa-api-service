package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ActorUserRoleTest {

  @Test
  void enum_has_exact_canonical_values_in_order() {
    String[] expected =
        new String[] {
          "ROLE_INPUT",
          "ROLE_APPROVE",
          "ROLE_USER_MBR",
          "ROLE_USER_EPR",
          "ROLE_AGENT",
          "ROLE_ITADMIN",
          "ROLE_CCD",
          "ROLE_SA",
          "ROLE_BROKER",
          "ROLE_CCC",
          "ROLE_OPERATION"
        };
    String[] actual =
        java.util.Arrays.stream(ActorUserRole.values()).map(Enum::name).toArray(String[]::new);
    assertArrayEquals(
        expected, actual, "ActorUserRole must contain exactly the approved ten values in order");
  }

  @Test
  void valueOf_accepts_canonical_and_rejects_legacy() {
    // canonical accepted
    assertEquals(ActorUserRole.ROLE_USER_MBR, ActorUserRole.valueOf("ROLE_USER_MBR"));

    // legacy values should not map; valueOf should throw IllegalArgumentException
    assertThrows(IllegalArgumentException.class, () -> ActorUserRole.valueOf("MEMBER"));
    assertThrows(IllegalArgumentException.class, () -> ActorUserRole.valueOf("role_user"));
    assertThrows(IllegalArgumentException.class, () -> ActorUserRole.valueOf("EMPLOYEE"));
  }
}
