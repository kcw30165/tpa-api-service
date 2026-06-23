package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.CleanUpAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.GetAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateEntryResult;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.CleanUpAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class ReferenceDateAllControllerTest {
    private static final String ALL_PATH = "/api/v1/internal/reference-date/all";

    @Test
    void getAllEndpointReturnsReferenceDatesFromUseCase() {
        AtomicBoolean called = new AtomicBoolean(false);
        GetAllReferenceDatesUseCase getAllUseCase = () -> {
            called.set(true);
            return Mono.just(new GetAllReferenceDatesResult(List.of(
                    new ReferenceDateEntryResult("HK", "01/01/2026"),
                    new ReferenceDateEntryResult("JP", "31/12/2025"))));
        };

        webClient(getAllUseCase, cleanUpUseCase(0L))
                .get()
                .uri(ALL_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.result.referenceDates[0].accountEnv").isEqualTo("HK")
                .jsonPath("$.result.referenceDates[0].refDate").isEqualTo("01/01/2026")
                .jsonPath("$.result.referenceDates[1].accountEnv").isEqualTo("JP");

        assertThat(called).isTrue();
    }

    @Test
    void deleteAllEndpointReturnsDeletedCountFromUseCase() {
        AtomicBoolean called = new AtomicBoolean(false);
        CleanUpAllReferenceDatesUseCase cleanUpUseCase = () -> {
            called.set(true);
            return Mono.just(new CleanUpAllReferenceDatesResult(2L));
        };

        webClient(getAllUseCase(), cleanUpUseCase)
                .delete()
                .uri(ALL_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("UPDATED")
                .jsonPath("$.result.deletedCount").isEqualTo(2);

        assertThat(called).isTrue();
    }

    @Test
    void deleteAllEndpointReturnsSuccessWhenNothingDeleted() {
        webClient(getAllUseCase(), cleanUpUseCase(0L))
                .delete()
                .uri(ALL_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("UPDATED")
                .jsonPath("$.result.deletedCount").isEqualTo(0);
    }

    private WebTestClient webClient(
            GetAllReferenceDatesUseCase getAllUseCase,
            CleanUpAllReferenceDatesUseCase cleanUpUseCase) {
        return WebTestClient.bindToController(new ReferenceDateController(
                        refreshUseCase(),
                        getAllUseCase,
                        cleanUpUseCase))
                .build();
    }

    private RefreshReferenceDateUseCase refreshUseCase() {
        return command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true));
    }

    private GetAllReferenceDatesUseCase getAllUseCase() {
        return () -> Mono.just(new GetAllReferenceDatesResult(List.of()));
    }

    private CleanUpAllReferenceDatesUseCase cleanUpUseCase(long deletedCount) {
        return () -> Mono.just(new CleanUpAllReferenceDatesResult(deletedCount));
    }
}
