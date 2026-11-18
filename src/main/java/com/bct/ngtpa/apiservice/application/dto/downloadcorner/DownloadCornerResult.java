package com.bct.ngtpa.apiservice.application.dto.downloadcorner;

import java.util.List;
import java.util.Objects;

public record DownloadCornerResult(List<DownloadCornerCategory> categories) {

  public DownloadCornerResult {
    categories = List.copyOf(Objects.requireNonNull(categories, "categories must not be null"));
  }
}
