package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Map;
import java.util.Objects;

public final class UpdateAuthFlowAttributesCommand {
  private final AuthFlowSessionId authFlowSessionId;
  private final Map<String, String> attributes;

  public UpdateAuthFlowAttributesCommand(
      AuthFlowSessionId authFlowSessionId, Map<String, String> attributes) {
    this.authFlowSessionId =
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    this.attributes = Objects.requireNonNull(attributes, "attributes must not be null");
  }

  public AuthFlowSessionId authFlowSessionId() {
    return authFlowSessionId;
  }

  public Map<String, String> attributes() {
    return attributes;
  }
}
