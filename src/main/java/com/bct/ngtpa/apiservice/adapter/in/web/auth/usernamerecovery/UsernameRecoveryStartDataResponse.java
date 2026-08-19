package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Public Start response data.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 * (session-created data carries {@code csrfToken}, {@code expiresInSeconds}, and {@code schemes};
 * the zero-registered terminal projection carries only {@code schemes}).
 *
 * <p>{@code csrfToken} and {@code expiresInSeconds} are omitted when null (Jackson {@code
 * NON_NULL}) so the zero-registered projection serializes as {@code {"schemes": [...]}} per the
 * approved example; {@code schemeRef: null} inside each scheme is preserved by {@link
 * UsernameRecoverySchemeResponse}. {@link #toString()} never discloses the CSRF token.
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
