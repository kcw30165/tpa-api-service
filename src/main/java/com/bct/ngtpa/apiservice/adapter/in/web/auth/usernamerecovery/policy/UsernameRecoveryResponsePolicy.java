package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import java.util.Objects;

/**
 * Typed Task 023 policy output for a Username Recovery feature outcome or exception: the completed
 * {@link MutationResponse} envelope plus an optional typed HTTP classification override.
 *
 * <p>Task 024 consumes this record: it delegates the body to {@code
 * WorkflowHttpResponses.toResponse(body)} or {@code WorkflowHttpResponses.toResponse(body,
 * httpOverride)} and never inspects error-code strings. This class never chooses an HTTP status and
 * never imports Spring, servlet, Reactor, controller, or cookie types.
 */
public record UsernameRecoveryResponsePolicy<T>(
    MutationResponse<T> body, HttpStatusClassificationOverride httpOverride) {

  public UsernameRecoveryResponsePolicy {
    Objects.requireNonNull(body, "body must not be null");
  }

  /** True when an explicit typed override accompanies the body. */
  public boolean hasHttpOverride() {
    return httpOverride != null;
  }
}
