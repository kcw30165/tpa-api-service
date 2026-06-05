package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfigProvider;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.ContributionSummaryWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.ContributionSortingSupport;
import com.bct.ngtpa.apiservice.adapter.in.web.support.ContributionSummaryWorkbookExporter;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.util.context.ContextView;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
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
                        @RequestParam(value = "fromDate", required = true) String fromDate,
                        @RequestParam(value = "toDate", required = true) String toDate,
                        @RequestParam(value = "lang", required = false) String lang,
                        @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
                        @RequestParam(value = "page", required = false) Integer page,
                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {

                int resolvedPage = page != null ? page : DEFAULT_PAGE;
                int resolvedPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;

                return Mono.deferContextual(contextView -> {
                        String resolvedLang = resolveLanguage(contextView, acceptLanguage, lang);
                        String accountRef = resolveRequiredAccountRef(contextView);
                        return getContributionSummaryUseCase
                                        .execute(new GetContributionSummaryCommand(
                                                        fromDate, toDate,
                                                        resolvedLang, resolvedPage, resolvedPageSize, accountRef))
                                        .map(contributionSortingSupport::sort)
                                        .map(result -> contributionSummaryWebMapper.toListResponse(
                                                        result,
                                                        contributionWebDisplayConfigProvider.get(),
                                                        resolvedLang,
                                                        result.accountEnv() != null ? result.accountEnv() : "",
                                                        resolvedPage,
                                                        resolvedPageSize));
                });
        }

        @GetMapping(value = "/contributions/export", produces = EXCEL_MEDIA_TYPE)
        public Mono<ResponseEntity<byte[]>> exportContributionSummary(
                        @RequestParam(value = "lang", required = false) String lang,
                        @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage) {
                return Mono.deferContextual(contextView -> {
                        String resolvedLang = resolveLanguage(contextView, acceptLanguage, lang);
                        String accountRef = resolveRequiredAccountRef(contextView);
                        return exportContributionSummaryUseCase.execute(new ExportContributionSummaryCommand(accountRef))
                                        .map(contributionSortingSupport::sort)
                                        .map(result -> contributionSummaryWorkbookExporter.write(result, resolvedLang))
                                        .map(body -> ResponseEntity.ok()
                                                        .contentType(MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
                                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                                        ContentDisposition.attachment()
                                                                                        .filename(EXPORT_FILE_NAME).build()
                                                                                        .toString())
                                                        .body(body));
                });
        }

        private String resolveLanguage(ContextView contextView, String acceptLanguage, String fallbackLang) {
                RequestHeaderContext requestHeaderContext = contextView.getOrDefault(
                                RequestHeaderContextKeys.CONTEXT_KEY,
                                null);
                return RequestLanguageResolver.resolve(requestHeaderContext, acceptLanguage, fallbackLang);
        }

        private String resolveRequiredAccountRef(ContextView contextView) {
                RequestHeaderContext requestHeaderContext = contextView.getOrDefault(
                                RequestHeaderContextKeys.CONTEXT_KEY,
                                null);
                if (requestHeaderContext == null || requestHeaderContext.accountRef() == null) {
                        throw new PortalAccessContextResolutionException(
                                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                                        "Missing Account-Ref header for selected-account API");
                }
                return requestHeaderContext.accountRef();
        }
}