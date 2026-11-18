package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.ApimField;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem.Category;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem.DocumentItem;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCategory;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerData;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class TrpGetDownloadCornerApimMapper {

  DownloadCornerData map(TrpGetDownloadCornerApimDataItem item) {
    if (item == null) {
      throw malformed(
          "response.data[0]",
          DownloadCornerMalformedResponseException.MalformedReason.INVALID_TYPE);
    }
    List<Category> categories = required(item.categories(), "response.data[0].categories");
    List<DownloadCornerCategory> mapped = new ArrayList<>();
    for (int index = 0; index < categories.size(); index++) {
      String categoryPath = indexed("response.data[0].categories", index);
      Category category = categories.get(index);
      if (category == null) {
        throw malformed(
            categoryPath, DownloadCornerMalformedResponseException.MalformedReason.INVALID_TYPE);
      }
      int categorySeq =
          requiredSequence(category.categorySeq(), path(categoryPath, "category-seq"));
      String categoryNameEng =
          requiredNonBlank(category.categoryNameEng(), path(categoryPath, "category-name-eng"));
      String categoryNameChi = optionalText(category.categoryNameChi());
      List<DocumentItem> items = required(category.items(), path(categoryPath, "items"));
      List<DownloadCornerItem> mappedItems = new ArrayList<>();
      for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
        String itemPath = indexed(path(categoryPath, "items"), itemIndex);
        DocumentItem document = items.get(itemIndex);
        if (document == null) {
          throw malformed(
              itemPath, DownloadCornerMalformedResponseException.MalformedReason.INVALID_TYPE);
        }
        int itemSeq = requiredSequence(document.itemSeq(), path(itemPath, "item-seq"));
        mappedItems.add(
            new DownloadCornerItem(
                itemSeq,
                optionalText(document.itemNameEng()),
                optionalText(document.itemNameChi()),
                optionalText(document.itemLinkEng()),
                optionalText(document.itemLinkChi()),
                optionalText(document.fileNameEng()),
                optionalText(document.fileNameChi())));
      }
      mapped.add(
          new DownloadCornerCategory(
              categorySeq, categoryNameEng, categoryNameChi, List.copyOf(mappedItems)));
    }
    return new DownloadCornerData(List.copyOf(mapped));
  }

  private static String requiredNonBlank(ApimField<String> field, String fieldPath) {
    String value = required(field, fieldPath);
    if (value.isBlank()) {
      throw malformed(
          fieldPath, DownloadCornerMalformedResponseException.MalformedReason.REQUIRED_TEXT_BLANK);
    }
    return value;
  }

  private static int requiredSequence(ApimField<BigDecimal> field, String fieldPath) {
    BigDecimal value = required(field, fieldPath);
    try {
      return value.intValueExact();
    } catch (ArithmeticException exception) {
      throw malformed(
          fieldPath, DownloadCornerMalformedResponseException.MalformedReason.INVALID_TYPE);
    }
  }

  private static <T> T required(ApimField<T> field, String fieldPath) {
    if (field == null || field.state() != ApimField.State.VALUE) {
      throw malformed(
          fieldPath,
          DownloadCornerMalformedResponseException.MalformedReason.MISSING_REQUIRED_FIELD);
    }
    return field.value();
  }

  private static String optionalText(ApimField<String> field) {
    return field == null || field.state() != ApimField.State.VALUE ? null : field.value();
  }

  private static String indexed(String field, int index) {
    return field + "[" + index + "]";
  }

  private static String path(String parent, String field) {
    return parent + "." + field;
  }

  private static DownloadCornerMalformedResponseException malformed(
      String fieldPath, DownloadCornerMalformedResponseException.MalformedReason reason) {
    return new DownloadCornerMalformedResponseException(fieldPath, reason);
  }
}
