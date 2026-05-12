package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfigProvider;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.ContributionSummaryWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionListResponse;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
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
        private static final int DEFAULT_PAGE = 1;
        private static final int DEFAULT_PAGE_SIZE = 99999;

        private final GetContributionSummaryUseCase getContributionSummaryUseCase;
        private final ExportContributionSummaryUseCase exportContributionSummaryUseCase;
        private final ContributionSummaryWorkbookExporter contributionSummaryWorkbookExporter;
        private final ContributionWebDisplayConfigProvider contributionWebDisplayConfigProvider;
        private final ContributionSummaryWebMapper contributionSummaryWebMapper;
        private final ContributionSortingSupport contributionSortingSupport;

        @GetMapping(value = "/contributions", produces = MediaType.APPLICATION_JSON_VALUE)
        public Mono<ContributionListResponse> getContributionSummary(
                        @RequestParam(value = "env", required = false) String env,
                        @RequestParam(value = "mbrType", required = false) String mbrType,
                        @RequestParam(value = "fromDate", required = false) String fromDate,
                        @RequestParam(value = "toDate", required = false) String toDate,
                        @RequestParam(value = "lang", required = false) String lang,
                        @RequestParam(value = "page", required = false) Integer page,
                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {

                int resolvedPage = page != null ? page : DEFAULT_PAGE;
                int resolvedPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
                String resolvedLang = (lang == null || lang.isBlank()) ? "en" : lang.trim();

                return getContributionSummaryUseCase
                                .execute(new GetContributionSummaryCommand(
                                                env, mbrType, fromDate, toDate,
                                                resolvedLang, resolvedPage, resolvedPageSize))
                                .map(contributionSortingSupport::sort)
                                .map(result -> contributionSummaryWebMapper.toListResponse(
                                                result,
                                                contributionWebDisplayConfigProvider.get(),
                                                fromDate != null ? fromDate : "",
                                                toDate != null ? toDate : "",
                                                resolvedLang,
                                                env != null ? env : "",
                                                resolvedPage,
                                                resolvedPageSize));
        }

        @GetMapping(value = "/contributions/export", produces = EXCEL_MEDIA_TYPE)
        public Mono<ResponseEntity<byte[]>> exportContributionSummary(
                        @RequestParam(value = "env", required = false) String env,
                        @RequestParam(value = "mbrType", required = false) String mbrType) {
                return exportContributionSummaryUseCase.execute(new ExportContributionSummaryCommand(env, mbrType))
                                .map(contributionSortingSupport::sort)
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