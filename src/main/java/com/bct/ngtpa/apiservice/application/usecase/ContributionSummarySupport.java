package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
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

    static void validatePagination(int page, int pageSize) {
        if (page <= 0) {
            throw new InvalidContributionRequestException("page must be greater than 0");
        }
        if (pageSize <= 0) {
            throw new InvalidContributionRequestException("pageSize must be greater than 0");
        }
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
            String coverFrom,
            String coverTo,
            PortalAccessContext context) {
        return new FetchContributionSummaryCommand(
                context.account().accountEnv(),
                context.memberOwner().memberType(),
                coverFrom,
                coverTo,
                context.account().policyNo(),
                context.account().certNo(),
                context.actor().actorUserId(),
                context.account().trustCode(),
                context.account().schemeType());
    }
}