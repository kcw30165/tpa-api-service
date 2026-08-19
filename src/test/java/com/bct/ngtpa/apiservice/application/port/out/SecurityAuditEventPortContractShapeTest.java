package com.bct.ngtpa.apiservice.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEvent;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SecurityAuditEventPortContractShapeTest {

  @Test
  void securityAuditEventPortExposesPublishOperation() throws Exception {
    var method = SecurityAuditEventPort.class.getMethod("publish", SecurityAuditEvent.class);

    assertEquals(Mono.class, method.getReturnType());
    assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
    var returnType = (ParameterizedType) method.getGenericReturnType();
    assertEquals(Void.class, returnType.getActualTypeArguments()[0]);
  }
}
