package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.application.dto.CleanUpAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.GetAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateEntryResult;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class ReferenceDateAllMutationResponseContractTest {
    private static final String ALL_PATH = "/api/v1/internal/reference-date/all";

    @Test
    void getAllUsesGenericBaseMutationResponseShape() {
        WebTestClient client = WebTestClient.bindToController(new ReferenceDateController(
                        command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true)),
                        () -> Mono.just(new GetAllReferenceDatesResult(List.of(
                                new ReferenceDateEntryResult("HK", "01/01/2026")))),
                        () -> Mono.just(new CleanUpAllReferenceDatesResult(0L))))
                .build();

        client.get()
                .uri(ALL_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.result.referenceDates[0].accountEnv").isEqualTo("HK")
                .jsonPath("$.messages").isArray()
                .jsonPath("$.messages.length()").isEqualTo(0)
                .jsonPath("$.errors").isArray()
                .jsonPath("$.errors.length()").isEqualTo(0);
    }

    @Test
    void deleteAllUsesGenericBaseMutationResponseShape() {
        WebTestClient client = WebTestClient.bindToController(new ReferenceDateController(
                        command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true)),
                        () -> Mono.just(new GetAllReferenceDatesResult(List.of())),
                        () -> Mono.just(new CleanUpAllReferenceDatesResult(0L))))
                .build();

        client.delete()
                .uri(ALL_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("UPDATED")
                .jsonPath("$.result.deletedCount").isEqualTo(0)
                .jsonPath("$.messages").isArray()
                .jsonPath("$.messages.length()").isEqualTo(0)
                .jsonPath("$.errors").isArray()
                .jsonPath("$.errors.length()").isEqualTo(0);
    }
}
