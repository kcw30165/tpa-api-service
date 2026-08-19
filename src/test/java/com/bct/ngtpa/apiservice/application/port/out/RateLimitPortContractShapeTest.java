package com.bct.ngtpa.apiservice.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitResult;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RateLimitPortContractShapeTest {

  @Test
  void rateLimitPortExposesCheckAndConsumeOperation() throws Exception {
    var method = RateLimitPort.class.getMethod("checkAndConsume", RateLimitCommand.class);

    assertEquals(Mono.class, method.getReturnType());
    assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
    var returnType = (ParameterizedType) method.getGenericReturnType();
    assertEquals(RateLimitResult.class, returnType.getActualTypeArguments()[0]);
  }
}
