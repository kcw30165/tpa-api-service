package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimRequest;
import com.bct.ngtpa.apiservice.infrastructure.jackson.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpGetDownloadCornerApimDtoContractTest {

  private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

  @Test
  void serializesExactlyFourKebabCaseRequestFields() throws Exception {
    var request =
        new TrpGetDownloadCornerApimRequest("actor-123", "role_user_mbr", "policy-456", "cert-789");

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

    assertThat(json.size()).isEqualTo(4);
    assertThat(json.get("login-user-id").textValue()).isEqualTo("actor-123");
    assertThat(json.get("login-user-role").textValue()).isEqualTo("role_user_mbr");
    assertThat(json.get("policy-no").textValue()).isEqualTo("policy-456");
    assertThat(json.get("cert-no").textValue()).isEqualTo("cert-789");
  }

  @Test
  void parsesTheLockedResponseWireNamesIncludingNestedCategoriesAndItems() throws Exception {
    String json =
        """
        {
          "env":"DB",
          "categories":[{
            "category-seq":2,
            "category-name-eng":"Forms",
            "category-name-chi":"表格",
            "items":[{
              "item-seq":4,
              "item-name-eng":"Member form",
              "item-name-chi":"成員表格",
              "item-link-eng":"https://example.test/member.pdf",
              "item-link-chi":"https://example.test/member-zh.pdf",
              "file-name-eng":"member.pdf",
              "file-name-chi":"member-zh.pdf"
            }]
          }]
        }
        """;

    var item = objectMapper.readValue(json, TrpGetDownloadCornerApimDataItem.class);

    assertThat(item.categories().value()).hasSize(1);
    var category = item.categories().value().getFirst();
    assertThat(category.categorySeq().value()).isEqualByComparingTo("2");
    assertThat(category.categoryNameEng().value()).isEqualTo("Forms");
    assertThat(category.categoryNameChi().value()).isEqualTo("表格");
    var document = category.items().value().getFirst();
    assertThat(document.itemSeq().value()).isEqualByComparingTo("4");
    assertThat(document.itemNameEng().value()).isEqualTo("Member form");
    assertThat(document.itemNameChi().value()).isEqualTo("成員表格");
    assertThat(document.itemLinkEng().value()).isEqualTo("https://example.test/member.pdf");
    assertThat(document.itemLinkChi().value()).isEqualTo("https://example.test/member-zh.pdf");
    assertThat(document.fileNameEng().value()).isEqualTo("member.pdf");
    assertThat(document.fileNameChi().value()).isEqualTo("member-zh.pdf");
  }
}
