package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountRefGeneratorPort;
import java.util.*;

/** Assigns session-scoped opaque references without persisting or exposing session transport. */
public final class LoginAccountReferenceAssignmentService {
  private final LoginAccountRefGeneratorPort generator;
  private final int maxGenerationAttempts;

  public LoginAccountReferenceAssignmentService(
      LoginAccountRefGeneratorPort generator, int maxGenerationAttempts) {
    this.generator = Objects.requireNonNull(generator);
    if (maxGenerationAttempts <= 0)
      throw new IllegalArgumentException("maxGenerationAttempts must be positive");
    this.maxGenerationAttempts = maxGenerationAttempts;
  }

  public LoginAccountReferenceAssignmentResult assign(List<LoginValidatedAccount> source) {
    List<LoginValidatedAccount> accounts = source == null ? List.of() : List.copyOf(source);
    if (accounts.isEmpty())
      throw new IllegalArgumentException("At least one validated account is required");
    Set<String> used = new HashSet<>();
    List<LoginSessionAccount> assigned = new ArrayList<>(accounts.size());
    for (LoginValidatedAccount account : accounts) {
      LoginAccountRef ref = nextUnique(used);
      assigned.add(
          new LoginSessionAccount(
              ref,
              account.accountEnv(),
              account.rawPolicyNo(),
              account.policyNo(),
              Integer.toString(account.rawCertNo()),
              account.certNo(),
              account.trustCode(),
              account.schemeType(),
              account.termStatus(),
              account.termCompletionDate()));
    }
    return new LoginAccountReferenceAssignmentResult(assigned);
  }

  private LoginAccountRef nextUnique(Set<String> used) {
    for (int attempt = 0; attempt < maxGenerationAttempts; attempt++) {
      LoginAccountRef candidate =
          Objects.requireNonNull(
              generator.generate(), "generated account reference must not be null");
      if (used.add(candidate.value())) return candidate;
    }
    throw new IllegalStateException("Unable to generate unique account reference");
  }
}
