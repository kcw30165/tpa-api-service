package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.config.ContributionSummaryProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReport;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryRow;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContributionControllerTest {

    @Test
    void returnsContributionSummaryJsonAndPassesQueryParamsToUseCase() {
        AtomicReference<GetContributionSummaryCommand> captured = new AtomicReference<>();
        GetContributionSummaryUseCase getUseCase = command -> {
            captured.set(command);
            return Mono.just(sampleResult());
        };

        WebTestClient client = webClient(getUseCase, unusedExportUseCase());

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions")
                        .queryParam("env", "JP")
                        .queryParam("mbrType", "MBR")
                        .queryParam("fromDate", "05/04/2026")
                        .queryParam("toDate", "05/05/2026")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.contributions[0].dealingDate").isEqualTo("01/03/2026")
                .jsonPath("$.contributions[0].coveringPeriod").isEqualTo("01/03/2026 - 31/03/2026")
                .jsonPath("$.contributions[0].totalContribution").isEqualTo("HKD24908.45")
                .jsonPath("$.contributions[0].details[0].labels.en").isEqualTo("Total")
                .jsonPath("$.contributions[0].details[1].labels.en").isEqualTo("Company")
                .jsonPath("$.contributions[0].details[2].amount").isEqualTo("HKD7116.7");

        assertEquals("JP", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
        assertEquals("05/04/2026", captured.get().fromDate());
        assertEquals("05/05/2026", captured.get().toDate());
    }

    @Test
    void returnsBadRequestForInvalidContributionDateQuery() {
        GetContributionSummaryUseCase getUseCase = command -> Mono.error(
                new InvalidContributionRequestException("fromDate must be provided in dd/MM/yyyy format"));

        WebTestClient client = webClient(getUseCase, unusedExportUseCase());

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions")
                        .queryParam("env", "JP")
                        .queryParam("mbrType", "MBR")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("400")
                .jsonPath("$.message").isEqualTo("fromDate must be provided in dd/MM/yyyy format")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void returnsExcelDownloadForContributionExport() {
        AtomicReference<ExportContributionSummaryCommand> captured = new AtomicReference<>();
        ExportContributionSummaryUseCase exportUseCase = command -> {
            captured.set(command);
            return Mono.just(sampleResult());
        };

        WebTestClient client = webClient(unusedGetUseCase(), exportUseCase);

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions/export")
                        .queryParam("env", "JP")
                        .queryParam("mbrType", "MBR")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(ContributionController.EXCEL_MEDIA_TYPE)
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"Contribution_Summary.xlsx\"")
                .expectBody()
                .consumeWith(result -> assertTrue(result.getResponseBody() != null && result.getResponseBody().length > 0));

        assertEquals("JP", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
    }

    private WebTestClient webClient(
            GetContributionSummaryUseCase getContributionSummaryUseCase,
            ExportContributionSummaryUseCase exportContributionSummaryUseCase) {
        var properties = new ContributionSummaryProperties();
        return WebTestClient.bindToController(new ContributionController(
                        getContributionSummaryUseCase,
                        exportContributionSummaryUseCase,
                        new ContributionSummaryWorkbookExporter(properties),
                        properties))
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private GetContributionSummaryUseCase unusedGetUseCase() {
        return command -> Mono.just(sampleResult());
    }

    private ExportContributionSummaryUseCase unusedExportUseCase() {
        return command -> Mono.just(sampleResult());
    }

    private ContributionSummaryReportResult sampleResult() {
        return new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(
                                new ContributionSource("ER", new ContributionLabels("Company", ""), 10),
                                new ContributionSource("EE", new ContributionLabels("Member", ""), 20)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026",
                                "01/03/2026",
                                "31/03/2026",
                                new BigDecimal("24908.45"),
                                new LinkedHashMap<>(java.util.Map.of(
                                        "ER", new BigDecimal("17791.75"),
                                        "EE", new BigDecimal("7116.7")))))),
                "HKD");
    }
}