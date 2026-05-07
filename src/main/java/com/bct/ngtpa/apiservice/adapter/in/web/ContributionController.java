package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.config.ContributionSummaryProperties;
import com.bct.ngtpa.apiservice.config.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContributionController {

        static final String EXCEL_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        private static final String EXPORT_FILE_NAME = "Contribution_Summary.xlsx";

        private final GetContributionSummaryUseCase getContributionSummaryUseCase;
        private final ExportContributionSummaryUseCase exportContributionSummaryUseCase;
        private final ContributionSummaryWorkbookExporter contributionSummaryWorkbookExporter;
        private final ContributionSummaryProperties contributionSummaryProperties;

        @GetMapping(value = "/contributions", produces = MediaType.APPLICATION_JSON_VALUE)
        @LogExecution(value = "contributions.get", logArgs = true)
        public Mono<ContributionSummaryResponse> getContributionSummary(
                        @RequestParam(value = "env", required = false) String env,
                        @RequestParam(value = "mbrType", required = false) String mbrType,
                        @RequestParam(value = "fromDate", required = false) String fromDate,
                        @RequestParam(value = "toDate", required = false) String toDate) {
                return getContributionSummaryUseCase
                                .execute(new GetContributionSummaryCommand(env, mbrType, fromDate, toDate))
                                .map(result -> ContributionSummaryResponse.from(result, contributionSummaryProperties));
        }

        @GetMapping(value = "/contributions/export", produces = EXCEL_MEDIA_TYPE)
        @LogExecution(value = "contributions.export", logArgs = true)
        public Mono<ResponseEntity<byte[]>> exportContributionSummary(
                        @RequestParam(value = "env", required = false) String env,
                        @RequestParam(value = "mbrType", required = false) String mbrType) {
                return exportContributionSummaryUseCase.execute(new ExportContributionSummaryCommand(env, mbrType))
                                .map(contributionSummaryWorkbookExporter::write)
                                .map(body -> ResponseEntity.ok()
                                                .contentType(MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
                                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                                ContentDisposition.attachment()
                                                                                .filename(EXPORT_FILE_NAME).build()
                                                                                .toString())
                                                .body(body));
        }
}