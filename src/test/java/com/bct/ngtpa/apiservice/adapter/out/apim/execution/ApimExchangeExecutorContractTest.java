package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ApimExchangeExecutorContractTest {
  @Test
  void exposesOnlyTypedEnvelopePostBoundaryAndHasNoDefaultImplementation() throws Exception {
    assertThat(ApimExchangeExecutor.class.isInterface()).isTrue();
    Method[] methods = ApimExchangeExecutor.class.getDeclaredMethods();
    assertThat(methods).hasSize(1);
    Method method = methods[0];
    assertThat(method.getName()).isEqualTo("postEnvelope");
    assertThat(method.isDefault()).isFalse();
    assertThat(method.getParameterTypes()).containsExactly(ApimExchangeRequest.class);
    assertThat(method.getGenericReturnType().getTypeName())
        .contains(Mono.class.getName(), ApimResponseEnvelope.class.getName());
  }
}
