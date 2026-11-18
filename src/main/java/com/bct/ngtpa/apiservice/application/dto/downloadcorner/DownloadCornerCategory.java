package com.bct.ngtpa.apiservice.application.dto.downloadcorner;

import java.util.List;
import java.util.Objects;

public record DownloadCornerCategory(
    int categorySeq,
    String categoryNameEng,
    String categoryNameChi,
    List<DownloadCornerItem> items) {

  public DownloadCornerCategory {
    Objects.requireNonNull(categorySeq, "categorySeq must not be null");
    Objects.requireNonNull(categoryNameEng, "categoryNameEng must not be null");
    items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
  }
}
