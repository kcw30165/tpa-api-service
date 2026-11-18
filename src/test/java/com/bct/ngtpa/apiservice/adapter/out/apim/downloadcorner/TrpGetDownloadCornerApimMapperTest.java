package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.ApimField;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem.Category;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem.DocumentItem;
import com.bct.ngtpa.apiservice.infrastructure.jackson.JacksonConfig;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpGetDownloadCornerApimMapperTest {

  private final TrpGetDownloadCornerApimMapper mapper = new TrpGetDownloadCornerApimMapper();

  @Test
  void mapsRawBilingualValuesAndSequencesAndIgnoresEnvironment() throws Exception {
    var item =
        new JacksonConfig()
            .objectMapper()
            .readValue(
                """
                {
                  "env":"IGNORED",
                  "categories":[{
                    "category-seq":8,
                    "category-name-eng":"English category",
                    "category-name-chi":"中文類別",
                    "items":[{
                      "item-seq":3,
                      "item-name-eng":"English item",
                      "item-name-chi":"中文項目",
                      "item-link-eng":"https://example.test/en",
                      "item-link-chi":"https://example.test/zh",
                      "file-name-eng":"en.pdf",
                      "file-name-chi":"zh.pdf"
                    }]
                  }]
                }
                """,
                TrpGetDownloadCornerApimDataItem.class);

    var result = mapper.map(item);

    assertThat(result.categories()).hasSize(1);
    var category = result.categories().getFirst();
    assertThat(category.categorySeq()).isEqualTo(8);
    assertThat(category.categoryNameEng()).isEqualTo("English category");
    assertThat(category.categoryNameChi()).isEqualTo("中文類別");
    assertThat(category.items()).hasSize(1);
    var document = category.items().getFirst();
    assertThat(document.itemSeq()).isEqualTo(3);
    assertThat(document.itemNameEng()).isEqualTo("English item");
    assertThat(document.itemNameChi()).isEqualTo("中文項目");
    assertThat(document.itemLinkEng()).isEqualTo("https://example.test/en");
    assertThat(document.itemLinkChi()).isEqualTo("https://example.test/zh");
    assertThat(document.fileNameEng()).isEqualTo("en.pdf");
    assertThat(document.fileNameChi()).isEqualTo("zh.pdf");
  }

  @Test
  void mapsMissingAndNullOptionalValuesToNullAndPreservesEmptyLists() throws Exception {
    var item =
        new JacksonConfig()
            .objectMapper()
            .readValue(
                """
                {"categories":[
                  {"category-seq":1,"category-name-eng":"Forms","items":[]},
                  {"category-seq":2,"category-name-eng":"Other","category-name-chi":null,
                   "items":[{"item-seq":1,"item-name-eng":null,"item-link-eng":null,
                     "file-name-eng":null,"item-name-chi":null,"item-link-chi":null,
                     "file-name-chi":null}]}
                ]}
                """,
                TrpGetDownloadCornerApimDataItem.class);

    var result = mapper.map(item);

    assertThat(result.categories()).hasSize(2);
    assertThat(result.categories().getFirst().items()).isEmpty();
    var category = result.categories().get(1);
    assertThat(category.categoryNameChi()).isNull();
    var document = category.items().getFirst();
    assertThat(document.itemNameEng()).isNull();
    assertThat(document.itemLinkEng()).isNull();
    assertThat(document.fileNameEng()).isNull();
    assertThat(document.itemNameChi()).isNull();
    assertThat(document.itemLinkChi()).isNull();
    assertThat(document.fileNameChi()).isNull();
  }

  @Test
  void rejectsNullRecordsAndNullListEntries() {
    assertMalformed(() -> mapper.map(null), "response.data[0]");
    assertMalformed(
        () -> mapper.map(new TrpGetDownloadCornerApimDataItem(null)),
        "response.data[0].categories");
    assertMalformed(
        () ->
            mapper.map(
                new TrpGetDownloadCornerApimDataItem(
                    ApimField.value(Arrays.asList((Category) null)))),
        "response.data[0].categories[0]");

    var category =
        new Category(
            ApimField.value(BigDecimal.ONE),
            ApimField.value("Forms"),
            ApimField.absent(),
            ApimField.value(Arrays.asList((DocumentItem) null)));
    assertMalformed(
        () -> mapper.map(new TrpGetDownloadCornerApimDataItem(ApimField.value(List.of(category)))),
        "response.data[0].categories[0].items[0]");
  }

  @Test
  void mapsNullOptionalFieldReferencesToNull() {
    var document =
        new DocumentItem(ApimField.value(BigDecimal.ONE), null, null, null, null, null, null);
    var category =
        new Category(
            ApimField.value(BigDecimal.ONE),
            ApimField.value("Forms"),
            null,
            ApimField.value(List.of(document)));

    var result =
        mapper.map(new TrpGetDownloadCornerApimDataItem(ApimField.value(List.of(category))));

    assertThat(result.categories().getFirst().categoryNameChi()).isNull();
    var mappedDocument = result.categories().getFirst().items().getFirst();
    assertThat(mappedDocument.itemNameEng()).isNull();
    assertThat(mappedDocument.itemNameChi()).isNull();
    assertThat(mappedDocument.itemLinkEng()).isNull();
    assertThat(mappedDocument.itemLinkChi()).isNull();
    assertThat(mappedDocument.fileNameEng()).isNull();
    assertThat(mappedDocument.fileNameChi()).isNull();
  }

  private static void assertMalformed(Runnable mapping, String expectedField) {
    assertThatThrownBy(mapping::run)
        .isInstanceOf(DownloadCornerMalformedResponseException.class)
        .satisfies(
            failure ->
                assertThat(((DownloadCornerMalformedResponseException) failure).malformedField())
                    .isEqualTo(expectedField));
  }
}
