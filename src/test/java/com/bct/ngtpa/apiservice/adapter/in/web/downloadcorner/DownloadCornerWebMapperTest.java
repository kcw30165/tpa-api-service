package com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerCategoryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerItemResponse;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCategory;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerItem;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerResult;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DownloadCornerWebMapperTest {

  private final DownloadCornerWebMapper mapper = new DownloadCornerWebMapper();

  @Test
  void mapsEnglishValues() {
    var item =
        item(
            1,
            "English item",
            "中文項目",
            " https://example.test/en.pdf ",
            "https://example.test/zh.pdf",
            "en.pdf",
            "zh.pdf");

    DownloadCornerCategoryResponse category =
        mapper
            .toResultResponse(
                result(List.of(category(1, "English category", "中文類別", List.of(item)))), "en")
            .data()
            .categories()
            .getFirst();

    assertThat(category.categoryName()).isEqualTo("English category");
    assertThat(category.items())
        .containsExactly(
            new DownloadCornerItemResponse(
                "English item", " https://example.test/en.pdf ", "en.pdf"));
  }

  @Test
  void mapsCompleteChineseValues() {
    var item =
        item(
            1,
            "English item",
            "中文項目",
            "https://example.test/en.pdf",
            "https://example.test/zh.pdf",
            "en.pdf",
            "zh.pdf");

    DownloadCornerCategoryResponse category =
        mapper
            .toResultResponse(
                result(List.of(category(1, "English category", "中文類別", List.of(item)))), "zh-HK")
            .data()
            .categories()
            .getFirst();

    assertThat(category.categoryName()).isEqualTo("中文類別");
    assertThat(category.items())
        .containsExactly(
            new DownloadCornerItemResponse("中文項目", "https://example.test/zh.pdf", "zh.pdf"));
  }

  @ParameterizedTest
  @MethodSource("missingChineseValues")
  void fallsBackToEnglishIndependentlyForEveryChineseField(Field field, String missingValue) {
    var category =
        category(
            1,
            "English category",
            field == Field.CATEGORY_NAME ? missingValue : "中文類別",
            List.of(
                item(
                    1,
                    "English item",
                    field == Field.ITEM_NAME ? missingValue : "中文項目",
                    "https://example.test/en.pdf",
                    field == Field.ITEM_LINK ? missingValue : "https://example.test/zh.pdf",
                    "en.pdf",
                    field == Field.FILE_NAME ? missingValue : "zh.pdf")));

    DownloadCornerCategoryResponse response =
        mapper.toResultResponse(result(List.of(category)), "zh").data().categories().getFirst();

    assertThat(response.categoryName())
        .isEqualTo(field == Field.CATEGORY_NAME ? "English category" : "中文類別");
    DownloadCornerItemResponse item = response.items().getFirst();
    assertThat(item.itemName()).isEqualTo(field == Field.ITEM_NAME ? "English item" : "中文項目");
    assertThat(item.itemLink())
        .isEqualTo(
            field == Field.ITEM_LINK
                ? "https://example.test/en.pdf"
                : "https://example.test/zh.pdf");
    assertThat(item.fileName()).isEqualTo(field == Field.FILE_NAME ? "en.pdf" : "zh.pdf");
  }

  @Test
  void dropsOnlyItemsWithBlankResolvedLinksAndKeepsNonblankLinksVerbatim() {
    var blankLink = item(1, "Dropped", "不顯示", null, "  ", "dropped.pdf", "dropped-zh.pdf");
    String verbatimLink = "  https://example.test/file.pdf  ";
    var linkedItem = item(2, "Kept", "保留", verbatimLink, null, "kept.pdf", null);

    DownloadCornerCategoryResponse response =
        mapper
            .toResultResponse(
                result(List.of(category(1, "Forms", "表格", List.of(blankLink, linkedItem)))), "zh")
            .data()
            .categories()
            .getFirst();

    assertThat(response.items())
        .containsExactly(new DownloadCornerItemResponse("保留", verbatimLink, "kept.pdf"));
  }

  @Test
  void keepsAnEmptyCategoryWithAnEmptyItemsList() {
    var response =
        mapper
            .toResultResponse(result(List.of(category(1, "Forms", "表格", List.of()))), "en")
            .data()
            .categories();

    assertThat(response).containsExactly(new DownloadCornerCategoryResponse("Forms", List.of()));
  }

  @Test
  void sortsCategoriesAndItemsIndependentlyAndPreservesOrderOnTies() {
    var input =
        List.of(
            category(
                2,
                "C",
                "C-zh",
                List.of(
                    item(3, "C3", "C3-zh", "c3", null, "c3", null),
                    item(1, "C1-first", "C1-first-zh", "c1a", null, "c1a", null),
                    item(1, "C1-second", "C1-second-zh", "c1b", null, "c1b", null))),
            category(1, "A", "A-zh", List.of(item(1, "A1", "A1-zh", "a1", null, "a1", null))),
            category(1, "B", "B-zh", List.of(item(1, "B1", "B1-zh", "b1", null, "b1", null))));

    List<DownloadCornerCategoryResponse> categories =
        mapper.toResultResponse(result(input), "en").data().categories();

    assertThat(categories)
        .extracting(DownloadCornerCategoryResponse::categoryName)
        .containsExactly("A", "B", "C");
    assertThat(categories.get(2).items())
        .extracting(DownloadCornerItemResponse::itemName)
        .containsExactly("C1-first", "C1-second", "C3");
  }

  private static Stream<Arguments> missingChineseValues() {
    return Stream.of(Field.values())
        .flatMap(field -> Stream.of((String) null, "  ").map(value -> Arguments.of(field, value)));
  }

  private static DownloadCornerResult result(List<DownloadCornerCategory> categories) {
    return new DownloadCornerResult(categories);
  }

  private static DownloadCornerCategory category(
      int sequence, String english, String chinese, List<DownloadCornerItem> items) {
    return new DownloadCornerCategory(sequence, english, chinese, items);
  }

  private static DownloadCornerItem item(
      int sequence,
      String englishName,
      String chineseName,
      String englishLink,
      String chineseLink,
      String englishFileName,
      String chineseFileName) {
    return new DownloadCornerItem(
        sequence,
        englishName,
        chineseName,
        englishLink,
        chineseLink,
        englishFileName,
        chineseFileName);
  }

  private enum Field {
    CATEGORY_NAME,
    ITEM_NAME,
    ITEM_LINK,
    FILE_NAME
  }
}
