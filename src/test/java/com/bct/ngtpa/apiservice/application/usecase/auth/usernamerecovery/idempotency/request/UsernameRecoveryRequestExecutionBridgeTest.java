package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestExecutionBridgeTest {
  private final JacksonTransientJsonAdapter json =
      new JacksonTransientJsonAdapter(new ObjectMapper());
  private final UsernameRecoveryRequestExecutionBridge bridge =
      new UsernameRecoveryRequestExecutionBridge(
          json,
          new UsernameRecoveryRequestReplayCodec(
              new UsernameRecoveryIdempotencyResponseEnvelope(json)));

  @Test
  void acquiredResponseIsPreservedAndReplayRestoresOriginalPublicOutcome() {
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null, null);
    HydratedIdempotentResponse transientResponse = bridge.toTransientResponse(result, 500);
    var captured = bridge.capture(transientResponse).block();
    assertThat(captured.response()).isEqualTo(transientResponse);
    assertThat(bridge.fromTransientResponse(bridge.hydrate(captured.snapshot()).block()))
        .isEqualTo(result);
  }

  @Test
  void malformedTransientResponseFailsClosed() {
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(200, "application/json", "{bad", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
