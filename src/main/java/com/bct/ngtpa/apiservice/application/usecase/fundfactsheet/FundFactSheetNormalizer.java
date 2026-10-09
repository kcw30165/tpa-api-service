package com.bct.ngtpa.apiservice.application.usecase.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetData;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetRow;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import com.bct.ngtpa.apiservice.application.service.presentation.UrlValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns the raw downstream rows into the normalized result, in downstream order.
 *
 * <p>A row without {@code seq} is dropped. A later row with an already seen {@code seq} is dropped
 * and the first one is kept. A dropped row is not inspected any further. A blank or null English
 * name is kept and reported. Chinese names are carried as received. Each link is validated by the
 * shared {@link UrlValidator}: a valid link is kept verbatim, an absent or malformed link becomes
 * null. Every event is value-free.
 */
public final class FundFactSheetNormalizer {

  private final UrlValidator urlValidator;
  private final FundFactSheetPresentationEventPort events;

  public FundFactSheetNormalizer(
      UrlValidator urlValidator, FundFactSheetPresentationEventPort events) {
    this.urlValidator = Objects.requireNonNull(urlValidator, "urlValidator must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
  }

  public FundFactSheetResult normalize(RawFundFactSheetData raw) {
    Objects.requireNonNull(raw, "raw must not be null");
    Set<Integer> seen = new HashSet<>();
    List<FundFactSheetRowResult> rows = new ArrayList<>(raw.rows().size());
    for (RawFundFactSheetRow row : raw.rows()) {
      Integer seq = row.seq();
      if (seq == null) {
        events.missingSeq();
        continue;
      }
      if (!seen.add(seq)) {
        events.duplicateSeq();
        continue;
      }
      String nameEng = row.nameEng();
      if (nameEng == null || nameEng.isBlank()) {
        events.blankEnglishName();
      }
      rows.add(
          new FundFactSheetRowResult(
              seq,
              nameEng,
              row.nameChi(),
              urlValidator.validate(row.linkEng()).orElse(null),
              urlValidator.validate(row.linkChi()).orElse(null)));
    }
    return new FundFactSheetResult(rows);
  }
}
