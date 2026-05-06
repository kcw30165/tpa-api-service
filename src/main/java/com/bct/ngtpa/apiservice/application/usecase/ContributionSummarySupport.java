package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class ContributionSummarySupport {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final String HARDCODED_POLICY_NO = "00000000217";
    static final String HARDCODED_CERT_NO = "95";
    static final String HARDCODED_USER_ID = "C402400A";
    static final LocalDate HARDCODED_REF_DATE = LocalDate.parse("01/10/2025", DATE_FORMATTER);
    static final String EMPTY_TRUST_CODE = "";
    static final String EMPTY_SCHEME_TYPE = "";

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

    static FetchContributionSummaryCommand newFetchCommand(String env, String mbrType, String coverFrom, String coverTo) {
        return new FetchContributionSummaryCommand(
                env,
                mbrType,
                coverFrom,
                coverTo,
                HARDCODED_POLICY_NO,
                HARDCODED_CERT_NO,
                HARDCODED_USER_ID,
                EMPTY_TRUST_CODE,
                EMPTY_SCHEME_TYPE);
    }
}