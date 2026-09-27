package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.math.BigInteger;
import java.util.List;

/**
 * Mirrors {@link FundPriceQueryResult}'s shape for the export path, plus {@code
 * unitPricePrecision} -- resolved once by {@link
 * com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCase} via {@link
 * com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver} (fund-pri/PRECISION,
 * mirroring GetAccountBalanceService's exact established pattern) and carried forward here so the
 * downstream {@code FundPriceWorkbookExporter} never needs to resolve metadata itself -- mirrors
 * how {@code AccountBalanceAssembler} receives an already-resolved {@code
 * ResolvedGeneralMetadataSet}, never resolving it itself.
 */
public record FundPriceExportResult(
    List<FundPriceRowResult> rows,
    String fundPriceType,
    String maxRiskLevel,
    BigInteger unitPricePrecision) {

  public FundPriceExportResult {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
