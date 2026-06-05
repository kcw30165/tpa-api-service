package com.bct.ngtpa.apiservice.adapter.in.web.support;

import com.bct.ngtpa.apiservice.adapter.in.web.sort.ApplySorts;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.ApplySortsAspect;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortBy;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortDirection;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortList;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortType;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import org.springframework.stereotype.Component;

/**
 * Web-adapter sorting support for the Contribution Summary feature.
 *
 * <p>This component applies presentation-layer sorting to a {@link ContributionSummaryReportResult}
 * before it is mapped to a JSON response or written to an Excel workbook. Actual sorting is
 * performed transparently by
 * {@link com.bct.ngtpa.apiservice.adapter.in.web.sort.ApplySortsAspect}; the method body is an
 * intentional pass-through.
 *
 * <p>Sort rules applied:
 * <ul>
 *   <li>{@code report.rows} – by {@code dealingDate} DESC, {@code coverFrom} DESC,
 *       {@code coverTo} DESC (all {@code dd/MM/yyyy} date strings). Null/blank/unparseable dates
 *       are placed last.</li>
 *   <li>{@code report.sources} – by {@code sequence} ASC then {@code code} ASC. This determines
 *       breakdown column order in both the JSON response and the Excel export.</li>
 * </ul>
 *
 * <p>This is the <em>first</em> usage of the generic {@link ApplySorts} framework. Future
 * endpoints can reuse the same annotation pattern without modifying this class.
 */
@Component
public class ContributionSortingSupport {

    /**
     * Receives a {@link ContributionSummaryReportResult} and returns it sorted according to the
     * contribution summary display rules. The {@link ApplySortsAspect} intercepts this call and
     * applies the declared sorts; this method body is a pass-through.
     *
     * @param result the unsorted result from the use case
     * @return the sorted result (populated by AOP)
     */
    @ApplySorts({
        @SortList(
            path = "report.rows",
            by = {
                @SortBy(
                    field = "dealingDate",
                    direction = SortDirection.DESC,
                    type = SortType.DATE,
                    datePattern = "dd/MM/yyyy"
                ),
                @SortBy(
                    field = "coverFrom",
                    direction = SortDirection.DESC,
                    type = SortType.DATE,
                    datePattern = "dd/MM/yyyy"
                ),
                @SortBy(
                    field = "coverTo",
                    direction = SortDirection.DESC,
                    type = SortType.DATE,
                    datePattern = "dd/MM/yyyy"
                )
            }
        ),
        @SortList(
            path = "report.sources",
            by = {
                @SortBy(
                    field = "sequence",
                    direction = SortDirection.ASC,
                    type = SortType.NUMBER
                ),
                @SortBy(
                    field = "code",
                    direction = SortDirection.ASC,
                    type = SortType.STRING
                )
            }
        )
    })
    public ContributionSummaryReportResult sort(ContributionSummaryReportResult result) {
        return result;
    }
}
