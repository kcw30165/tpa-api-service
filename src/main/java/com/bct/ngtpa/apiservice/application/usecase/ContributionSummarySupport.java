package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class ContributionSummarySupport {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ContributionSummarySupport() {
    }

    static LocalDate parseRequiredDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidContributionRequestException(fieldName + " must be provided in dd/MM/yyyy format");
        }

        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new InvalidContributionRequestException(fieldName + " must be provided in dd/MM/yyyy format");
        }
    }

    static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    static void validateDateRangeWithinReferenceWindow(LocalDate fromDate, LocalDate toDate, LocalDate refDate) {
        if (fromDate.isAfter(toDate)) {
            throw new InvalidContributionRequestException("fromDate must not be after toDate");
        }

        var minimumDate = refDate.minusMonths(36);
        if (fromDate.isBefore(minimumDate)
                || fromDate.isAfter(refDate)
                || toDate.isBefore(minimumDate)
                || toDate.isAfter(refDate)) {
            throw new InvalidContributionRequestException(
                    "fromDate and toDate must be within the range from ref-date minus 36 months to ref-date");
        }
    }

    static FetchContributionSummaryCommand newFetchCommand(
            String env,
            String mbrType,
            String coverFrom,
            String coverTo,
            MemberContext memberContext) {
        return new FetchContributionSummaryCommand(
                env,
                mbrType,
                coverFrom,
                coverTo,
                memberContext.policyNo(),
                memberContext.certNo(),
                memberContext.userId(),
                memberContext.trustCode(),
                memberContext.schemeType());
    }
}