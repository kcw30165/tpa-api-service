package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundFactSheetResponseTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode tree(Object value) {
    return MAPPER.valueToTree(value);
  }

  private static List<String> names(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  @Test
  void aRowIsWrittenWithExactlyTheDocumentedFieldNames() {
    var row =
        new FundFactSheetRowResponse(7, "Fund A", "Fund A", "zh", "https://example.test/a.pdf");

    assertThat(names(tree(row)))
        .containsExactlyInAnyOrder("seq", "fundName", "fundNameEn", "fundNameZh", "factsheetUrl");
    assertThat(tree(row).get("seq").asInt()).isEqualTo(7);
    assertThat(tree(row).get("fundName").asText()).isEqualTo("Fund A");
    assertThat(tree(row).get("factsheetUrl").asText()).isEqualTo("https://example.test/a.pdf");
  }

  @Test
  void aNullChineseNameIsWrittenAsNullAndNotOmitted() {
    var json = tree(new FundFactSheetRowResponse(1, "Fund A", "Fund A", null, null));

    assertThat(json.has("fundNameZh")).isTrue();
    assertThat(json.get("fundNameZh").isNull()).isTrue();
  }

  @Test
  void aNullFactsheetUrlIsOmitted() {
    var json = tree(new FundFactSheetRowResponse(1, "Fund A", "Fund A", "zh", null));

    assertThat(json.has("factsheetUrl")).isFalse();
    assertThat(names(json))
        .containsExactlyInAnyOrder("seq", "fundName", "fundNameEn", "fundNameZh");
  }

  @Test
  void blankValuesArePassedThroughAndNeverOmitted() {
    var json = tree(new FundFactSheetRowResponse(1, "", "", "", ""));

    assertThat(json.get("fundName").asText()).isEqualTo("");
    assertThat(json.get("fundNameEn").asText()).isEqualTo("");
    assertThat(json.get("fundNameZh").asText()).isEqualTo("");
    assertThat(json.has("factsheetUrl")).isTrue();
  }

  @Test
  void theEnvelopeCarriesSuccessStatusRowsAndEmptyMessagesAndErrors() {
    var data =
        new FundFactSheetResponse(
            List.of(new FundFactSheetRowResponse(1, "Fund A", "Fund A", null, null)));
    var envelope =
        MutationResponse.success(
            ApiStatus.SUCCESS, new FundFactSheetResultResponse(data), List.of());

    var json = tree(envelope);

    assertThat(names(json))
        .containsExactlyInAnyOrder("success", "status", "result", "messages", "errors");
    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
    assertThat(json.get("messages").isArray()).isTrue();
    assertThat(json.get("messages").size()).isEqualTo(0);
    assertThat(json.get("errors").isArray()).isTrue();
    assertThat(json.get("errors").size()).isEqualTo(0);
    assertThat(names(json.get("result"))).containsExactly("data");
    assertThat(names(json.get("result").get("data"))).containsExactly("rows");
    assertThat(json.get("result").get("data").get("rows").size()).isEqualTo(1);
  }

  @Test
  void rowsAreAlwaysPresentAndEmptyWhenNull() {
    var response = new FundFactSheetResponse(null);

    assertThat(response.rows()).isEmpty();
    var json = tree(response);
    assertThat(json.get("rows").isArray()).isTrue();
    assertThat(json.get("rows").size()).isEqualTo(0);
  }

  @Test
  void theResponseCopiesItsRowsAndIsUnmodifiable() {
    List<FundFactSheetRowResponse> source = new ArrayList<>();
    source.add(new FundFactSheetRowResponse(1, "A", "A", null, null));
    var response = new FundFactSheetResponse(source);

    source.add(new FundFactSheetRowResponse(2, "B", "B", null, null));

    assertThat(response.rows()).hasSize(1);
    var extra = new FundFactSheetRowResponse(3, "C", "C", null, null);
    assertThatThrownBy(() -> response.rows().add(extra))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void theResultResponseRequiresItsData() {
    assertThatThrownBy(() -> new FundFactSheetResultResponse(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("data");
  }

  @Test
  void onlyTheFactsheetUrlAccessorCarriesAnInclusionRuleAndNoClassCarriesOne() throws Exception {
    assertThat(FundFactSheetRowResponse.class.getAnnotation(JsonInclude.class)).isNull();
    assertThat(FundFactSheetResponse.class.getAnnotation(JsonInclude.class)).isNull();
    assertThat(FundFactSheetResultResponse.class.getAnnotation(JsonInclude.class)).isNull();

    for (String accessor : List.of("seq", "fundName", "fundNameEn", "fundNameZh")) {
      var method = FundFactSheetRowResponse.class.getDeclaredMethod(accessor);
      assertThat(method.getAnnotation(JsonInclude.class)).as(accessor).isNull();
    }
    var urlAccessor = FundFactSheetRowResponse.class.getDeclaredMethod("factsheetUrl");
    JsonInclude rule = urlAccessor.getAnnotation(JsonInclude.class);
    assertThat(rule).isNotNull();
    assertThat(rule.value()).isEqualTo(JsonInclude.Include.NON_NULL);
  }
}
