package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class MessageDeliveryPortContractShapeTest {

  @Test
  void messageDeliveryPortExposesDeliverOperation() {
    Method deliver = method("deliver", MessageDeliveryCommand.class);

    assertThat(deliver.getReturnType()).isEqualTo(Mono.class);
    assertThat(deliver.getGenericReturnType().getTypeName())
        .isEqualTo(Mono.class.getName() + "<" + MessageDeliveryResult.class.getName() + ">");
  }

  private Method method(String name, Class<?> parameterType) {
    return Arrays.stream(MessageDeliveryPort.class.getMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .filter(
            candidate ->
                Arrays.equals(candidate.getParameterTypes(), new Class<?>[] {parameterType}))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing method: " + name));
  }
}
