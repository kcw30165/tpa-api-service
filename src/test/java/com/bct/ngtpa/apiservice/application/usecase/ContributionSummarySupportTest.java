package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ContributionSummarySupportTest {
  @Test
  void formatDateProducesDdMmYyyyString() {
    assertEquals("31/03/2026", ContributionSummarySupport.formatDate(LocalDate.of(2026, 3, 31)));
  }

  @Test
  void parseRequiredDateAcceptsValidDdMmYyyy() {
    assertEquals(
        LocalDate.of(2026, 1, 1),
        ContributionSummarySupport.parseRequiredDate("01/01/2026", "fromDate"));
  }

  @Test
  void parseRequiredDateRejectsNullAndWrongFormat() {
    assertEquals(
        "fromDate must be provided in dd/MM/yyyy format",
        assertThrows(
                InvalidContributionRequestException.class,
                () -> ContributionSummarySupport.parseRequiredDate(null, "fromDate"))
            .getMessage());
    assertEquals(
        "toDate must be provided in dd/MM/yyyy format",
        assertThrows(
                InvalidContributionRequestException.class,
                () -> ContributionSummarySupport.parseRequiredDate("2026-01-01", "toDate"))
            .getMessage());
  }

  @Test
  void validatesPagination() {
    assertEquals(
        "page must be greater than 0",
        assertThrows(
                InvalidContributionRequestException.class,
                () -> ContributionSummarySupport.validatePagination(0, 10))
            .getMessage());
    assertEquals(
        "pageSize must be greater than 0",
        assertThrows(
                InvalidContributionRequestException.class,
                () -> ContributionSummarySupport.validatePagination(1, 0))
            .getMessage());
  }

  @Test
  void validatesInclusiveReferenceWindowAndOrdering() {
    LocalDate reference = LocalDate.of(2026, 3, 31);
    ContributionSummarySupport.validateDateRangeWithinReferenceWindow(
        LocalDate.of(2023, 3, 31), reference, reference);
    assertEquals(
        "fromDate must not be after toDate",
        assertThrows(
                InvalidContributionRequestException.class,
                () ->
                    ContributionSummarySupport.validateDateRangeWithinReferenceWindow(
                        reference, reference.minusDays(1), reference))
            .getMessage());
  }
}
