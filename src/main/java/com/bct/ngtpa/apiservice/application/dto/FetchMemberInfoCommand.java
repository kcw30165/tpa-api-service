package com.bct.ngtpa.apiservice.application.dto;

import java.util.Objects;

/** Focused outbound command for authenticated TRPGetMemberInfo retrieval. */
public final class FetchMemberInfoCommand {
  private final String accountEnv;
  private final String policyNo;
  private final String certNo;
  private final String userId;
  private final ActorUserRole actorUserRole;

  public FetchMemberInfoCommand(
      String accountEnv,
      String policyNo,
      String certNo,
      String userId,
      ActorUserRole actorUserRole) {
    this.accountEnv = accountEnv;
    this.policyNo = policyNo;
    this.certNo = certNo;
    this.userId = userId;
    this.actorUserRole = Objects.requireNonNull(actorUserRole, "actorUserRole");
  }

  /**
   * Compatibility constructor for existing callers and tests. Production retrieval supplies the
   * authenticated role explicitly.
   */
  public String getAccountEnv() {
    return accountEnv;
  }

  public String getPolicyNo() {
    return policyNo;
  }

  public String getCertNo() {
    return certNo;
  }

  public String getUserId() {
    return userId;
  }

  public ActorUserRole getActorUserRole() {
    return actorUserRole;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof FetchMemberInfoCommand that)) return false;
    return Objects.equals(accountEnv, that.accountEnv)
        && Objects.equals(policyNo, that.policyNo)
        && Objects.equals(certNo, that.certNo)
        && Objects.equals(userId, that.userId)
        && actorUserRole == that.actorUserRole;
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountEnv, policyNo, certNo, userId, actorUserRole);
  }
}
