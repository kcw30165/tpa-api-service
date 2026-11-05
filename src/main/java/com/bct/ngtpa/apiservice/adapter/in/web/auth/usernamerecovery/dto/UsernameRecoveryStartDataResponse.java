package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Public Start response data.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 * (session-created data carries {@code csrfToken}, {@code expiresInSeconds}, and {@code schemes},
 * including when every scheme is unregistered).
 *
 * <p>{@code csrfToken} and {@code expiresInSeconds} are omitted when null (Jackson {@code
 * NON_NULL}); a session-created Start result always carries both. {@link #toString()} never
 * discloses the CSRF token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsernameRecoveryStartDataResponse(
    String csrfToken, Integer expiresInSeconds, List<UsernameRecoverySchemeResponse> schemes) {

  public UsernameRecoveryStartDataResponse {
    schemes = schemes == null ? List.of() : List.copyOf(schemes);
  }

  @Override
  public String toString() {
    return "UsernameRecoveryStartDataResponse[csrfToken=<redacted>, expiresInSeconds="
        + expiresInSeconds
        + ", schemeCount="
        + schemes.size()
        + "]";
  }
}
