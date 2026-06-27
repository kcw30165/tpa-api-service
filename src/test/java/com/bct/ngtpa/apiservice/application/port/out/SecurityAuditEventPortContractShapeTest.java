package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
