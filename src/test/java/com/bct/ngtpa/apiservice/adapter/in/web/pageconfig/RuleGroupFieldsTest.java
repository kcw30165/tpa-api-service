package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleGroupFieldsTest {

  @Test
  void readsAListOfFieldNames() {
    assertThat(RuleGroupFields.of(List.of("a", "b"))).containsExactly("a", "b");
  }

  @Test
  void dropsNullListElementsAndConvertsOtherElementsToText() {
    List<Object> raw = new ArrayList<>(Arrays.asList("a", null, 7));

    assertThat(RuleGroupFields.of(raw)).containsExactly("a", "7");
  }

  @Test
  void readsAnIndexKeyedMapInNumericOrder() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("10", "k");
    raw.put("2", "c");
    raw.put("0", "a");
    raw.put("1", "b");

    assertThat(RuleGroupFields.of(raw)).containsExactly("a", "b", "c", "k");
  }

  @Test
  void skipsNullValuesOfAnIndexKeyedMap() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("0", "a");
    raw.put("1", null);
    raw.put("2", "c");

    assertThat(RuleGroupFields.of(raw)).containsExactly("a", "c");
  }

  @Test
  void treatsMapsThatAreNotIndexKeyedAsEmpty() {
    Map<String, Object> nullKey = new HashMap<>();
    nullKey.put(null, "a");

    assertThat(RuleGroupFields.of(Map.of())).isEmpty();
    assertThat(RuleGroupFields.of(Map.of("first", "a"))).isEmpty();
    assertThat(RuleGroupFields.of(Map.of("0", "a", "name", "b"))).isEmpty();
    assertThat(RuleGroupFields.of(Map.of("-1", "a"))).isEmpty();
    assertThat(RuleGroupFields.of(Map.of("1234567890", "a"))).isEmpty();
    assertThat(RuleGroupFields.of(nullKey)).isEmpty();
  }

  @Test
  void treatsAnyOtherValueAsEmpty() {
    assertThat(RuleGroupFields.of(null)).isEmpty();
    assertThat(RuleGroupFields.of("a")).isEmpty();
    assertThat(RuleGroupFields.of(5)).isEmpty();
  }
}
