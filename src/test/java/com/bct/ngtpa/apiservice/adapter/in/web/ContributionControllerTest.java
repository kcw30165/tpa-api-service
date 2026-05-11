package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfigProvider;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.ContributionSummaryWebMapper;
import com.bct.ngtpa.apiservice.application.dto.ContributionActions;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionSummaryProperties;
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
    void returnsContributionListJsonAndPassesQueryParamsToUseCase() {
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
                        .queryParam("fromDate", "01/03/2026")
                        .queryParam("toDate", "31/03/2026")
                        .queryParam("lang", "en")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "99999")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.actions.export.enabled").isEqualTo(true)
                .jsonPath("$.items[0].itemId").isEqualTo("CONTRIB-2026-03")
                .jsonPath("$.items[0].itemType").isEqualTo("contribution")
                .jsonPath("$.items[0].period.fromDate.value").isEqualTo("2026-03-01")
                .jsonPath("$.items[0].period.fromDate.text").isEqualTo("01/03/2026")
                .jsonPath("$.items[0].period.toDate.value").isEqualTo("2026-03-31")
                .jsonPath("$.items[0].period.toDate.text").isEqualTo("31/03/2026")
                .jsonPath("$.items[0].dealingDate.value").isEqualTo("2026-03-01")
                .jsonPath("$.items[0].dealingDate.text").isEqualTo("01/03/2026")
                .jsonPath("$.items[0].currency.value").isEqualTo("HKD")
                .jsonPath("$.items[0].currency.text").isEqualTo("HKD")
                .jsonPath("$.items[0].totalContribution.amount.value").isEqualTo(24908.45)
                .jsonPath("$.items[0].breakdown.rows[0].label").isEqualTo("Total Contributions")
                .jsonPath("$.items[0].breakdown.rows[1].label").isEqualTo("Company")
                .jsonPath("$.items[0].breakdown.rows[2].label").isEqualTo("Member")
                .jsonPath("$.pagination.page").isEqualTo(1)
                .jsonPath("$.pagination.pageSize").isEqualTo(99999)
                .jsonPath("$.pagination.totalRecords").isEqualTo(1)
                .jsonPath("$.pagination.hasNextPage").isEqualTo(false);

        assertEquals("JP", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
        assertEquals("01/03/2026", captured.get().fromDate());
        assertEquals("31/03/2026", captured.get().toDate());
        assertEquals("en", captured.get().lang());
        assertEquals(1, captured.get().page());
        assertEquals(99999, captured.get().pageSize());
    }

    @Test
    void usesDefaultPageAndPageSizeWhenNotProvided() {
        AtomicReference<GetContributionSummaryCommand> captured = new AtomicReference<>();
        GetContributionSummaryUseCase getUseCase = command -> {
            captured.set(command);
            return Mono.just(sampleResult());
        };

        WebTestClient client = webClient(getUseCase, unusedExportUseCase());

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions")
                        .queryParam("env", "JP")
                        .queryParam("fromDate", "01/03/2026")
                        .queryParam("toDate", "31/03/2026")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pagination.page").isEqualTo(1)
                .jsonPath("$.pagination.pageSize").isEqualTo(99999);

        assertEquals(1, captured.get().page());
        assertEquals(99999, captured.get().pageSize());
    }

    @Test
    void defaultsLangToEnWhenNotProvided() {
        AtomicReference<GetContributionSummaryCommand> captured = new AtomicReference<>();
        GetContributionSummaryUseCase getUseCase = command -> {
            captured.set(command);
            return Mono.just(sampleResult());
        };

        webClient(getUseCase, unusedExportUseCase())
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions")
                        .queryParam("fromDate", "01/03/2026")
                        .queryParam("toDate", "31/03/2026")
                        .build())
                .exchange()
                .expectStatus().isOk();

        assertEquals("en", captured.get().lang());
    }

    @Test
    void returnsBadRequestWhenPageIsZero() {
        GetContributionSummaryUseCase getUseCase = command -> Mono.error(
                new InvalidContributionRequestException("page must be greater than 0"));

        webClient(getUseCase, unusedExportUseCase())
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions")
                        .queryParam("fromDate", "01/03/2026")
                        .queryParam("toDate", "31/03/2026")
                        .queryParam("page", "0")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("400")
                .jsonPath("$.message").isEqualTo("page must be greater than 0");
    }

    @Test
    void returnsBadRequestWhenPageSizeIsZero() {
        GetContributionSummaryUseCase getUseCase = command -> Mono.error(
                new InvalidContributionRequestException("pageSize must be greater than 0"));

        webClient(getUseCase, unusedExportUseCase())
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/contributions")
                        .queryParam("fromDate", "01/03/2026")
                        .queryParam("toDate", "31/03/2026")
                        .queryParam("pageSize", "0")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("400")
                .jsonPath("$.message").isEqualTo("pageSize must be greater than 0");
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
                .jsonPath("$.message").isEqualTo("fromDate must be provided in dd/MM/yyyy format");
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
        var provider = displayConfigProvider();
        var mapper = new ContributionSummaryWebMapper(
                (amount, lang, env, trustCode, schemeType) -> amount == null ? "0" : amount.toPlainString());
        return WebTestClient.bindToController(new ContributionController(
                        getContributionSummaryUseCase,
                        exportContributionSummaryUseCase,
                        new ContributionSummaryWorkbookExporter(provider),
                        provider,
                        mapper))
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ContributionWebDisplayConfigProvider displayConfigProvider() {
        var properties = new ContributionSummaryProperties();
        return new ContributionWebDisplayConfigProvider(properties);
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
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true),
                "",
                "");
    }
}
