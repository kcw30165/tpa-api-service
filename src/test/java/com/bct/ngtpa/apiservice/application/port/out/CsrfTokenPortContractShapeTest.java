package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.security.csrf.CreateCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.CreateCsrfTokenResult;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.ValidateCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.ValidateCsrfTokenResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CsrfTokenPortContractShapeTest {

    @Test
    void csrfTokenPortExposesLifecycleOperations() throws Exception {
        var create = CsrfTokenPort.class.getMethod("createToken", CreateCsrfTokenCommand.class);
        var validate = CsrfTokenPort.class.getMethod("validateToken", ValidateCsrfTokenCommand.class);
        var invalidate = CsrfTokenPort.class.getMethod("invalidateToken", String.class);

        assertMonoReturnType(create.getGenericReturnType(), CreateCsrfTokenResult.class);
        assertMonoReturnType(validate.getGenericReturnType(), ValidateCsrfTokenResult.class);
        assertEquals(Mono.class, invalidate.getReturnType());
        assertInstanceOf(ParameterizedType.class, invalidate.getGenericReturnType());
        var invalidateReturnType = (ParameterizedType) invalidate.getGenericReturnType();
        assertEquals(Void.class, invalidateReturnType.getActualTypeArguments()[0]);
    }

    private void assertMonoReturnType(java.lang.reflect.Type genericReturnType, Class<?> expectedPayloadType) {
        assertInstanceOf(ParameterizedType.class, genericReturnType);
        var returnType = (ParameterizedType) genericReturnType;
        assertEquals(Mono.class, returnType.getRawType());
        assertEquals(expectedPayloadType, returnType.getActualTypeArguments()[0]);
    }
}
