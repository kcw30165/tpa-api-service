package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import com.bct.ngtpa.apiservice.adapter.in.web.sort.ApplySorts;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortBy;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortDirection;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortEngine;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortList;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortType;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import java.lang.reflect.Method;
import org.springframework.stereotype.Component;

/**
 * Web-adapter sorting support for Fund Price -- the SECOND consumer of the generic
 * SortEngine/{@link ApplySorts} framework ({@code ContributionSortingSupport} being the first).
 *
 * <p><b>Query mode</b> ({@link #sort(FundPriceQueryResult)}): rows ordered by risk level ascending
 * (blank/unparseable ranks highest via {@link SortEngine}'s existing null-on-unparseable +
 * nulls-last semantics, needing no special-casing), then English fund name ascending, then unit
 * price ascending (the downstream bid-price under both fund-price-type values), per
 * fund-price-api-contract.md.
 *
 * <p><b>Export mode</b> ({@link #sort(FundPriceExportResult)}): a DELIBERATELY DIFFERENT sort
 * order, per fund-price-excel-export-contract.md's "Rows and sorting" section (confirmed, script
 * 134's investigation): price date descending (latest first), then risk level ascending (blank
 * ranks last), then English fund name ascending. {@code priceDate} is a plain {@link
 * java.time.LocalDate}, whose default {@code toString()} is ISO-8601 -- compatible with {@link
 * SortEngine}'s no-{@code datePattern} default parsing (confirmed, script 136's {@code
 * SortEngine} dump), so no {@code datePattern} is declared here.
 *
 * <p>Deliberately does NOT rely on Spring AOP proxy interception the way {@code
 * ContributionSortingSupport} does: this class must be directly unit-testable via plain
 * construction ({@code new FundPriceSortingSupport()}), so each sort method resolves its OWN
 * declared {@link ApplySorts} annotation via reflection and invokes {@link SortEngine} directly,
 * rather than depending on {@code ApplySortsAspect} to intercept a Spring-managed bean proxy. This
 * is a deliberate, documented divergence in *invocation mechanism* only, not in sort semantics.
 */
@Component
public class FundPriceSortingSupport {

  private final SortEngine sortEngine = new SortEngine();

  @ApplySorts({
    @SortList(
        path = "rows",
        by = {
          @SortBy(field = "riskLevel", direction = SortDirection.ASC, type = SortType.NUMBER),
          @SortBy(field = "fundName", direction = SortDirection.ASC, type = SortType.STRING),
          @SortBy(field = "bidPrice", direction = SortDirection.ASC, type = SortType.NUMBER)
        })
  })
  public FundPriceQueryResult sort(FundPriceQueryResult result) {
    return (FundPriceQueryResult) applySelfAnnotation(result, FundPriceQueryResult.class);
  }

  @ApplySorts({
    @SortList(
        path = "rows",
        by = {
          @SortBy(field = "priceDate", direction = SortDirection.DESC, type = SortType.DATE),
          @SortBy(field = "riskLevel", direction = SortDirection.ASC, type = SortType.NUMBER),
          @SortBy(field = "fundName", direction = SortDirection.ASC, type = SortType.STRING)
        })
  })
  public FundPriceExportResult sort(FundPriceExportResult result) {
    return (FundPriceExportResult) applySelfAnnotation(result, FundPriceExportResult.class);
  }

  private Object applySelfAnnotation(Object target, Class<?> paramType) {
    try {
      Method method = FundPriceSortingSupport.class.getMethod("sort", paramType);
      ApplySorts applySorts = method.getAnnotation(ApplySorts.class);
      return sortEngine.sort(target, applySorts);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "FundPriceSortingSupport: failed to resolve own sort annotation", e);
    }
  }
}
