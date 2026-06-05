package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import sun.misc.Unsafe;

class ApimWebClientFacadePrivateBranchTest {

    @Test
    void mapUpstreamErrorCodeCoversServiceUnavailableTimeoutAndGenericFailures() throws Exception {
        Object facade = allocateFacade();
        Method method = ApimWebClientFacade.class.getDeclaredMethod("mapUpstreamErrorCode", org.springframework.http.HttpStatusCode.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(facade, HttpStatus.SERVICE_UNAVAILABLE))
                .isEqualTo(ErrorCodes.APIM_SERVICE_UNAVAILABLE);
        assertThat((String) method.invoke(facade, HttpStatus.GATEWAY_TIMEOUT))
                .isEqualTo(ErrorCodes.APIM_TIMEOUT);
        assertThat((String) method.invoke(facade, HttpStatus.BAD_GATEWAY))
                .isEqualTo(ErrorCodes.APIM_UPSTREAM_FAILURE);
    }

    @Test
    void extractApimErrorCodeHandlesBlankMalformedAndJsonBodies() throws Exception {
        Object facade = allocateFacade();
        Method method = ApimWebClientFacade.class.getDeclaredMethod("extractApimErrorCode", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(facade, "   ")).isNull();
        assertThat((String) method.invoke(facade, "not-json")).isNull();

        assertThatCode(() -> method.invoke(facade, "{\"errorCode\":\"err.apim.response.invalid\"}"))
                .doesNotThrowAnyException();
        assertThatCode(() -> method.invoke(facade, "{\"code\":\"err.apim.timeout\"}"))
                .doesNotThrowAnyException();
    }

    private static Object allocateFacade() throws Exception {
        return unsafe().allocateInstance(ApimWebClientFacade.class);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
