package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateResult;
import reactor.core.publisher.Mono;

/**
 * Generic, reusable compare-and-swap update capability for {@code AuthenticatedSession}.
 *
 * <p>Per fund-price-session-contract.md ("Mutation safety"): this port is intentionally NOT
 * scenario-specific and NOT CSRF-specific. It mirrors the revision-token mechanism already proven
 * for {@code AuthFlowSessionPort}'s revisioned mutations, generalized to a single field-agnostic
 * method so feature and lifecycle authenticated-session updates can reuse it, rather than
 * introducing another feature-specific CAS port.
 *
 * <p>The command selects either atomic current-TTL preservation or an absolute expiry deadline. A
 * missing key is never created and only revision mismatch is eligible for caller retry.
 */
public interface AuthenticatedSessionCasUpdatePort {

  Mono<AuthenticatedSessionCasUpdateResult> updateIfCurrent(
      AuthenticatedSessionCasUpdateCommand command);
}
