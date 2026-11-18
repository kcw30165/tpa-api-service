package com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner;

import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerCategoryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerItemResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCategory;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerItem;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerResult;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DownloadCornerWebMapper {

  public DownloadCornerResultResponse toResultResponse(
      DownloadCornerResult result, String language) {
    Objects.requireNonNull(result, "result must not be null");
    boolean chinese = RequestLanguageResolver.isZhHk(language);

    List<DownloadCornerCategoryResponse> categories =
        result.categories().stream()
            .sorted(Comparator.comparingInt(DownloadCornerCategory::categorySeq))
            .map(category -> category(category, chinese))
            .toList();
    return new DownloadCornerResultResponse(new DownloadCornerDataResponse(categories));
  }

  private static DownloadCornerCategoryResponse category(
      DownloadCornerCategory category, boolean chinese) {
    List<DownloadCornerItemResponse> items =
        category.items().stream()
            .sorted(Comparator.comparingInt(DownloadCornerItem::itemSeq))
            .map(item -> item(item, chinese))
            .filter(item -> hasText(item.itemLink()))
            .toList();
    return new DownloadCornerCategoryResponse(
        selected(category.categoryNameEng(), category.categoryNameChi(), chinese), items);
  }

  private static DownloadCornerItemResponse item(DownloadCornerItem item, boolean chinese) {
    return new DownloadCornerItemResponse(
        selected(item.itemNameEng(), item.itemNameChi(), chinese),
        selected(item.itemLinkEng(), item.itemLinkChi(), chinese),
        selected(item.fileNameEng(), item.fileNameChi(), chinese));
  }

  private static String selected(String english, String chineseValue, boolean chinese) {
    if (!chinese) {
      return english;
    }
    return hasText(chineseValue) ? chineseValue : english;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
