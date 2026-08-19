package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartCommand;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RegistrationIdempotencyPortContractShapeTest {

  @Test
  void registrationIdempotencyPortExposesExpectedOperations() throws Exception {
    Method start =
        RegistrationIdempotencyPort.class.getMethod(
            "startProcessing", RegistrationIdempotencyStartCommand.class);
    Method completed =
        RegistrationIdempotencyPort.class.getMethod(
            "markCompleted", RegistrationIdempotencyCompleteCommand.class);
    Method failed =
        RegistrationIdempotencyPort.class.getMethod(
            "markFailed", RegistrationIdempotencyFailCommand.class);
    Method load =
        RegistrationIdempotencyPort.class.getMethod(
            "load", RegistrationIdempotencyAction.class, RegistrationIdempotencyKey.class);
    Method delete =
        RegistrationIdempotencyPort.class.getMethod(
            "delete", RegistrationIdempotencyAction.class, RegistrationIdempotencyKey.class);

    assertThat(start.getReturnType()).isEqualTo(Mono.class);
    assertThat(completed.getReturnType()).isEqualTo(Mono.class);
    assertThat(failed.getReturnType()).isEqualTo(Mono.class);
    assertThat(load.getReturnType()).isEqualTo(Mono.class);
    assertThat(delete.getReturnType()).isEqualTo(Mono.class);
  }
}
