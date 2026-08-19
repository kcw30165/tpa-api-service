package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class SystemHttpResponsesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldReturn503SystemError() {
    StepVerifier.create(SystemHttpResponses.<String>systemError())
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(entity.getBody()).isNotNull();
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
              assertThat(entity.getBody().result()).isNull();
              assertThat(entity.getBody().messages()).isEmpty();
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .satisfies(
                      error -> {
                        assertThat(error.type()).isEqualTo("SYSTEM");
                        assertThat(error.code())
                            .isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
                        assertThat(error.targets()).isEmpty();
                        assertThat(error.source()).isEqualTo("SERVER");
                      });
            })
        .verifyComplete();
  }

  @Test
  void shouldNotExposeInternalDetails() throws Exception {
    var holder = new MutationResponseHolder();
    StepVerifier.create(SystemHttpResponses.<String>systemError())
        .assertNext(
            entity -> {
              assertThat(entity.getBody().errors().getFirst().message()).isNull();
              holder.body = entity.getBody();
            })
        .verifyComplete();

    String json = objectMapper.writeValueAsString(holder.body);
    assertThat(json)
        .doesNotContain("exception")
        .doesNotContain("stacktrace")
        .doesNotContain("Caused by")
        .doesNotContain("at com.bct.");
  }

  private static final class MutationResponseHolder {
    private MutationResponse<String> body;
  }
}
