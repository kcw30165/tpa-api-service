package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class NoticeTemplateRegistryPortContractShapeTest {

  @Test
  void noticeTemplateRegistryPortExposesResolveOperation() {
    Method resolve =
        method("resolve", MessageDeliveryType.class, MessageDeliveryChannel.class, String.class);
    assertThat(resolve.getReturnType()).isEqualTo(NoticeTemplateDefinition.class);
  }

  private Method method(String name, Class<?>... parameterTypes) {
    return Arrays.stream(NoticeTemplateRegistryPort.class.getMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing method: " + name));
  }
}
