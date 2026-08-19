package com.bct.ngtpa.apiservice.application.port.out.authflow;

/**
 * Protects a scenario-specific logical pre-auth context using a scenario-neutral technical
 * boundary. Authenticated portal sessions use a separate future port.
 */
public interface AuthFlowProtectedContextPort<C, E> {
  E protect(C context);

  C unprotect(E encryptedContext);
}
