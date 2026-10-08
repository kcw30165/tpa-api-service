package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response.FundFactSheetResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response.FundFactSheetResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response.FundFactSheetRowResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetRowResult;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Maps the normalized fund fact sheet to the public response.
 *
 * <p>Language selection happens here, not in the use case. For {@code zh_HK} the fund name is the
 * Chinese name, falling back to the English name when it is null or blank, and the link is the
 * Chinese link; for every other language both come from the English side. A link never falls back
 * to the other language. Both bilingual names are passed through as received.
 *
 * <p>Rows are ordered by the name the user sees, lower-cased with {@link Locale#ROOT} and compared
 * in plain character-code order (no collation), then by {@code seq}. A null or blank display name
 * sorts first. This is the same fund-name rule as Fund Price, implemented locally.
 */
@Component
public final class FundFactSheetWebMapper {

  private static final Comparator<FundFactSheetRowResponse> DISPLAY_ORDER =
      Comparator.comparing((FundFactSheetRowResponse row) -> caseless(row.fundName()))
          .thenComparingInt(FundFactSheetRowResponse::seq);

  public FundFactSheetResultResponse toResultResponse(FundFactSheetResult result, String language) {
    Objects.requireNonNull(result, "result must not be null");
    boolean chinese = RequestLanguageResolver.isZhHk(language);
    List<FundFactSheetRowResponse> rows =
        result.rows().stream().map(row -> toRow(row, chinese)).sorted(DISPLAY_ORDER).toList();
    return new FundFactSheetResultResponse(new FundFactSheetResponse(rows));
  }

  private static FundFactSheetRowResponse toRow(FundFactSheetRowResult row, boolean chinese) {
    String fundName = chinese && hasText(row.nameChi()) ? row.nameChi() : row.nameEng();
    String factsheetUrl = chinese ? row.linkChi() : row.linkEng();
    return new FundFactSheetRowResponse(
        row.seq(), fundName, row.nameEng(), row.nameChi(), factsheetUrl);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String caseless(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
