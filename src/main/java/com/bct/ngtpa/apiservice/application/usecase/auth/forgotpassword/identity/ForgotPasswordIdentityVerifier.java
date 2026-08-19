package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;

public final class ForgotPasswordIdentityVerifier {

  public MemberIdentityType mapPublicIdentityType(String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "GPN" -> MemberIdentityType.GPN;
      case "GPFS" -> MemberIdentityType.GPFS;
      case "STAFF_NO" -> MemberIdentityType.STAFFNO;
      case "ID" -> MemberIdentityType.ID;
      default -> null;
    };
  }

  public boolean matches(
      String requestedType, String requestedValue, String protectedType, String protectedValue) {
    MemberIdentityType requested = mapPublicIdentityType(requestedType);
    MemberIdentityType stored = mapProtectedIdentityType(protectedType);
    return requested != null
        && requested == stored
        && requestedValue != null
        && protectedValue != null
        && requestedValue.trim().equals(protectedValue.trim());
  }

  private MemberIdentityType mapProtectedIdentityType(String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "GPN" -> MemberIdentityType.GPN;
      case "GPFS" -> MemberIdentityType.GPFS;
      case "STAFF_NO", "STAFFNO" -> MemberIdentityType.STAFFNO;
      case "ID" -> MemberIdentityType.ID;
      default -> null;
    };
  }
}
