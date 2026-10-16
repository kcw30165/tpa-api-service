package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inbound web request body for {@code POST /api/v1/fund-comparison/prices}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.3. Deliberately performs no business
 * validation beyond null-safety on the {@code fundCodes} LIST REFERENCE itself -- the 1-5 unique
 * nonblank fund-code rule, individual null/blank entry rejection, and time-range syntax check are
 * {@code FundComparisonPricesWebMapper#validate}'s responsibility (Milestone 3, sub-task 3.2).
 *
 * <p>Uses {@link Collections#unmodifiableList} rather than {@link List#copyOf} (corrected, script
 * 062): {@code List.copyOf} rejects any null ELEMENT within the list, not just a null list
 * reference, which made it impossible to even construct this record from a request body containing
 * a null array entry -- the NPE fired during construction, before {@code validate()} ever ran,
 * preventing the intended public {@code err.fund-comparison.request.invalid} contract from ever
 * being reached for that case.
 */
public record FundComparisonPricesRequest(List<String> fundCodes, String timeRange) {

  public FundComparisonPricesRequest {
    fundCodes =
        fundCodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(fundCodes));
  }
}
