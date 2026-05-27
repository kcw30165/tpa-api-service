package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshReferenceDateServiceTest {

    @Test
    void commandRejectsNullAccountEnv() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshReferenceDateCommand(null));

        assertEquals("accountEnv must not be blank", ex.getMessage());
    }

    @Test
    void commandRejectsBlankAccountEnv() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshReferenceDateCommand("  "));

        assertEquals("accountEnv must not be blank", ex.getMessage());
    }

    @Test
    void passesAccountEnvUnchangedToApimFetchPortAndReturnsRefreshResult() {
        AtomicReference<String> capturedAccountEnv = new AtomicReference<>();
        ApimReferenceDateRefreshPort port = accountEnv -> {
            capturedAccountEnv.set(accountEnv);
            return Mono.just(LocalDate.of(2025, 12, 31));
        };

        RefreshReferenceDateUseCase service = new RefreshReferenceDateService(port);

        StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP")))
                .assertNext(result -> {
                    assertEquals("JP", capturedAccountEnv.get());
                    assertEquals(new RefreshReferenceDateResult("JP", "31/12/2025", false, false), result);
                })
                .verifyComplete();
    }

    @Test
    void applicationContractsUseApplicationFacingTypes() throws NoSuchMethodException {
        assertEquals(String.class, RefreshReferenceDateCommand.class.getRecordComponents()[0].getType());

        assertEquals(String.class, RefreshReferenceDateResult.class.getRecordComponents()[0].getType());
        assertEquals(String.class, RefreshReferenceDateResult.class.getRecordComponents()[1].getType());
        assertEquals(boolean.class, RefreshReferenceDateResult.class.getRecordComponents()[2].getType());
        assertEquals(boolean.class, RefreshReferenceDateResult.class.getRecordComponents()[3].getType());

        ParameterizedType apimReturnType = (ParameterizedType) ApimReferenceDateRefreshPort.class
                .getMethod("fetchReferenceDate", String.class)
                .getGenericReturnType();
        assertEquals(Mono.class, apimReturnType.getRawType());
        assertEquals(LocalDate.class, apimReturnType.getActualTypeArguments()[0]);

        ParameterizedType useCaseReturnType = (ParameterizedType) RefreshReferenceDateUseCase.class
                .getMethod("execute", RefreshReferenceDateCommand.class)
                .getGenericReturnType();
        assertEquals(Mono.class, useCaseReturnType.getRawType());
        assertEquals(RefreshReferenceDateResult.class, useCaseReturnType.getActualTypeArguments()[0]);
    }
}