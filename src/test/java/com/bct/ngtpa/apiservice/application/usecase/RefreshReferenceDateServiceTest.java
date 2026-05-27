package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
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
    void afterApimSuccess_upsertsReferenceDateConfigAndReturnsRefreshResult() {
        AtomicReference<String> capturedAccountEnv = new AtomicReference<>();
        ApimReferenceDateRefreshPort apimPort = accountEnv -> {
            capturedAccountEnv.set(accountEnv);
            return Mono.just(LocalDate.of(2025, 12, 31));
        };
        AtomicReference<ReferenceDateConfigUpsertCommand> capturedConfigCommand = new AtomicReference<>();
        ReferenceDateConfigPort configPort = command -> {
            capturedConfigCommand.set(command);
            return Mono.empty();
        };

        RefreshReferenceDateUseCase service = new RefreshReferenceDateService(apimPort, configPort);

        StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP")))
                .assertNext(result -> {
                    assertEquals("JP", capturedAccountEnv.get());
                    assertEquals(new ReferenceDateConfigUpsertCommand("reference-date.JP", "31/12/2025"),
                            capturedConfigCommand.get());
                    assertEquals(new RefreshReferenceDateResult("JP", "31/12/2025", true, false), result);
                })
                .verifyComplete();
    }

    @Test
    void configUpsertFailure_failsSafely() {
        ApimReferenceDateRefreshPort apimPort = accountEnv -> Mono.just(LocalDate.of(2025, 12, 31));
        ReferenceDateConfigPort configPort = command -> Mono.error(new IllegalStateException("config upsert failed"));

        RefreshReferenceDateUseCase service = new RefreshReferenceDateService(apimPort, configPort);

        StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP")))
                .expectErrorSatisfies(error -> {
                    assertEquals(IllegalStateException.class, error.getClass());
                    assertEquals("config upsert failed", error.getMessage());
                })
                .verify();
    }

    @Test
    void applicationContractsUseApplicationFacingTypes() throws NoSuchMethodException {
        assertEquals(String.class, RefreshReferenceDateCommand.class.getRecordComponents()[0].getType());
        assertEquals(String.class, ReferenceDateConfigUpsertCommand.class.getRecordComponents()[0].getType());
        assertEquals(String.class, ReferenceDateConfigUpsertCommand.class.getRecordComponents()[1].getType());

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

        ParameterizedType configReturnType = (ParameterizedType) ReferenceDateConfigPort.class
            .getMethod("upsertReferenceDate", ReferenceDateConfigUpsertCommand.class)
            .getGenericReturnType();
        assertEquals(Mono.class, configReturnType.getRawType());
        assertEquals(Void.class, configReturnType.getActualTypeArguments()[0]);
    }
}