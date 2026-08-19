package com.bct.ngtpa.apiservice.application.dto;

import java.util.Locale;

/** Strict canonical actor roles and explicit TAS/APIM wire mapping. */
public enum ActorUserRole {
  ROLE_INPUT,
  ROLE_APPROVE,
  ROLE_USER_MBR,
  ROLE_USER_EPR,
  ROLE_AGENT,
  ROLE_ITADMIN,
  ROLE_CCD,
  ROLE_SA,
  ROLE_BROKER,
  ROLE_CCC,
  ROLE_OPERATION;

  public String toApimValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static ActorUserRole fromApimValue(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("APIM actor role must not be blank");
    for (ActorUserRole role : values()) if (role.toApimValue().equals(value.trim())) return role;
    throw new IllegalArgumentException("Unsupported APIM actor role");
  }
}
