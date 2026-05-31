package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionActionsResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionAmountValueResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionBreakdownResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionBreakdownRowResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionCurrencyValueResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionDateValueResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionExportActionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionItemResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionItemType;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionPaginationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionPeriodResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionTotalContributionResponse;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.port.out.AmountDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.DateDisplayPort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContributionSummaryWebMapper {

    static final DateTimeFormatter APIM_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AmountDisplayPort amountDisplayPort;
    private final DateDisplayPort dateDisplayPort;

    public ContributionListResponse toListResponse(
            ContributionSummaryReportResult result,
            ContributionWebDisplayConfig displayConfig,
            String lang,
            String accountEnv,
            int page,
            int pageSize) {

        var trustCode = result.trustCode() != null ? result.trustCode() : "";
        var schemeType = result.schemeType() != null ? result.schemeType() : "";

        var currencyDisplay = result.currencyDisplay();
        var currencyCode = result.report().currency();
        var currencyText = resolveCurrencyText(currencyDisplay, lang);

        var rows = result.report().rows();
        var itemIds = buildItemIds(rows);

        List<ContributionItemResponse> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            items.add(toItemResponse(
                    rows.get(i), result, displayConfig, itemIds.get(i),
                    lang, accountEnv, trustCode, schemeType,
                    currencyCode, currencyText));
        }

        var actions = new ContributionActionsResponse(
                new ContributionExportActionResponse(
                        result.actions() != null && result.actions().exportEnabled()));

        var pagination = new ContributionPaginationResponse(page, pageSize, items.size(), false);

        return new ContributionListResponse(actions, List.copyOf(items), pagination);
    }

    private ContributionItemResponse toItemResponse(
            ContributionSummaryRow row,
            ContributionSummaryReportResult result,
            ContributionWebDisplayConfig displayConfig,
            String itemId,
            String lang,
            String accountEnv,
            String trustCode,
            String schemeType,
            String currencyCode,
            String currencyText) {

        // Period: value=ISO, text=formatted via DateDisplayPort from the actual row period dates
        LocalDate periodFrom = tryParseApimDate(row.coverFrom());
        LocalDate periodTo = tryParseApimDate(row.coverTo());
        String fromIso = periodFrom != null ? periodFrom.format(ISO_DATE_FORMATTER) : row.coverFrom();
        String toIso = periodTo != null ? periodTo.format(ISO_DATE_FORMATTER) : row.coverTo();
        String fromText = periodFrom != null
                ? dateDisplayPort.formatDate(periodFrom, lang, accountEnv, trustCode, schemeType)
                : (StringUtils.hasText(row.coverFrom()) ? row.coverFrom() : "");
        String toText = periodTo != null
                ? dateDisplayPort.formatDate(periodTo, lang, accountEnv, trustCode, schemeType)
                : (StringUtils.hasText(row.coverTo()) ? row.coverTo() : "");

        var period = new ContributionPeriodResponse(
                new ContributionDateValueResponse(fromIso, fromText),
                new ContributionDateValueResponse(toIso, toText));

        // Dealing date: value=ISO, text=formatted via DateDisplayPort (same as period dates)
        LocalDate dealing = tryParseApimDate(row.dealingDate());
        String dealingIso = dealing != null ? dealing.format(ISO_DATE_FORMATTER) : row.dealingDate();
        String dealingText = dealing != null
                ? dateDisplayPort.formatDate(dealing, lang, accountEnv, trustCode, schemeType)
                : (StringUtils.hasText(row.dealingDate()) ? row.dealingDate() : "");
        var dealingDateResponse = new ContributionDateValueResponse(dealingIso, dealingText);

        // Currency
        var currency = new ContributionCurrencyValueResponse(
                currencyCode != null ? currencyCode : "",
                currencyText);

        // Total contribution
        BigDecimal totalAmount = row.totalAmount() != null ? row.totalAmount() : BigDecimal.ZERO;
        String totalText = amountDisplayPort.formatAmount(totalAmount, lang, accountEnv, trustCode, schemeType);
        var totalContribution = new ContributionTotalContributionResponse(
                new ContributionAmountValueResponse(totalAmount, totalText));

        // Breakdown rows: total first, then by source order
        List<ContributionBreakdownRowResponse> breakdownRows = new ArrayList<>();
        breakdownRows.add(new ContributionBreakdownRowResponse(
            resolveTotalLabel(displayConfig, lang),
                new ContributionAmountValueResponse(totalAmount, totalText)));

        row.details(result.report().sources()).forEach(detail -> {
            var amt = detail.amount() != null ? detail.amount() : BigDecimal.ZERO;
            String amtText = amountDisplayPort.formatAmount(amt, lang, accountEnv, trustCode, schemeType);
            var label = resolveLabel(detail.source().labels(), lang);
            breakdownRows.add(new ContributionBreakdownRowResponse(
                    label,
                    new ContributionAmountValueResponse(amt, amtText)));
        });

        return new ContributionItemResponse(
                itemId,
                ContributionItemType.CONTRIBUTION,
                period,
                dealingDateResponse,
                currency,
                totalContribution,
                new ContributionBreakdownResponse(List.copyOf(breakdownRows)));
    }

    // --- Helpers ---

    private String resolveCurrencyText(com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay display, String lang) {
        if (display == null) return "";
        if (isZhHk(lang)) return display.zh() != null ? display.zh() : "";
        return display.en() != null ? display.en() : "";
    }

    private String resolveTotalLabel(ContributionWebDisplayConfig displayConfig, String lang) {
        if (isZhHk(lang)) return displayConfig.totalLabelZh();
        return displayConfig.totalLabelEn();
    }

    private String resolveLabel(com.bct.ngtpa.apiservice.domain.model.ContributionLabels labels, String lang) {
        if (labels == null) return "";
        if (isZhHk(lang)) return StringUtils.hasText(labels.zh()) ? labels.zh() : labels.en();
        return StringUtils.hasText(labels.en()) ? labels.en() : "";
    }

    private boolean isZhHk(String lang) {
        return RequestLanguageResolver.isZhHk(lang);
    }

    static LocalDate tryParseApimDate(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return LocalDate.parse(raw.trim(), APIM_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Builds a stable list of unique item IDs for the given rows.
     * If a base ID (CONTRIB-yyyy-MM) appears multiple times, duplicates are suffixed with -2, -3, etc.
     * The first occurrence keeps the unsuffixed base ID.
     */
    static List<String> buildItemIds(List<ContributionSummaryRow> rows) {
        Map<String, Integer> occurrenceCounts = new HashMap<>();
        for (var row : rows) {
            occurrenceCounts.merge(baseItemId(row), 1, Integer::sum);
        }
        Map<String, Integer> seenSoFar = new HashMap<>();
        List<String> ids = new ArrayList<>();
        for (var row : rows) {
            String base = baseItemId(row);
            if (occurrenceCounts.get(base) <= 1) {
                ids.add(base);
            } else {
                int seq = seenSoFar.merge(base, 1, Integer::sum);
                ids.add(seq == 1 ? base : base + "-" + seq);
            }
        }
        return List.copyOf(ids);
    }

    static String baseItemId(ContributionSummaryRow row) {
        LocalDate date = tryParseApimDate(row.coverFrom());
        if (date == null) return "CONTRIB-unknown";
        return "CONTRIB-" + date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}
