package com.bct.ngtpa.apiservice.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Session-cached field-access config (design section 5, D-11): identifiers plus item-id map only.
 */
class PersonalInformationConfigCacheEntryTest {

  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";

  private static Map<String, String> config() {
    return Map.of("addr1", "EDITABLE_COM", "email", "READONLY");
  }

  private static PersonalInformationConfigCacheEntry entry() {
    return new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, config());
  }

  @Test
  void keepsIdentifiersAndConfig() {
    var entry = entry();

    assertThat(entry.accountRef()).isEqualTo(ACCOUNT_REF);
    assertThat(entry.policyNo()).isEqualTo(POLICY_NO);
    assertThat(entry.itemConfig()).isEqualTo(config());
  }

  @Test
  void belongsToRequiresBothIdentifiers() {
    var entry = entry();

    assertThat(entry.belongsTo(ACCOUNT_REF, POLICY_NO)).isTrue();
    assertThat(entry.belongsTo("acc_otherotherotherotherother", POLICY_NO)).isFalse();
    assertThat(entry.belongsTo(ACCOUNT_REF, "00000009999")).isFalse();
    assertThat(entry.belongsTo(null, POLICY_NO)).isFalse();
    assertThat(entry.belongsTo(ACCOUNT_REF, null)).isFalse();
  }

  @Test
  void rejectsBlankOrNullAccountRefAndPolicyNo() {
    Map<String, String> config = config();

    assertThatThrownBy(() -> new PersonalInformationConfigCacheEntry(null, POLICY_NO, config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountRef");
    assertThatThrownBy(() -> new PersonalInformationConfigCacheEntry("  ", POLICY_NO, config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountRef");
    assertThatThrownBy(() -> new PersonalInformationConfigCacheEntry(ACCOUNT_REF, null, config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
    assertThatThrownBy(() -> new PersonalInformationConfigCacheEntry(ACCOUNT_REF, " ", config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsNullOrEmptyItemConfig() {
    Map<String, String> empty = Map.of();

    assertThatThrownBy(() -> new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("itemConfig");
    assertThatThrownBy(() -> new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, empty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("itemConfig");
  }

  @Test
  void rejectsBlankItemId() {
    Map<String, String> blankKey = new LinkedHashMap<>();
    blankKey.put(" ", "EDITABLE_COM");
    Map<String, String> nullKey = new LinkedHashMap<>();
    nullKey.put(null, "EDITABLE_COM");

    assertThatThrownBy(
            () -> new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, blankKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("item id");
    assertThatThrownBy(
            () -> new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, nullKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("item id");
  }

  @Test
  void nullConfigValueBecomesBlank() {
    Map<String, String> source = new LinkedHashMap<>();
    source.put("addr1", null);
    source.put("email", "READONLY");

    var entry = new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, source);

    assertThat(entry.itemConfig()).containsEntry("addr1", "").containsEntry("email", "READONLY");
  }

  @Test
  void copiesTheMapAndIsImmutable() {
    Map<String, String> source = new LinkedHashMap<>();
    source.put("a", "EDITABLE_COM");
    source.put("b", "READONLY");
    source.put("c", "HIDDEN");

    var entry = new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, source);
    source.put("d", "EDITABLE_OPTION");
    source.put("a", "HIDDEN");

    assertThat(entry.itemConfig())
        .containsExactly(
            Map.entry("a", "EDITABLE_COM"), Map.entry("b", "READONLY"), Map.entry("c", "HIDDEN"));
    Map<String, String> itemConfig = entry.itemConfig();
    assertThatThrownBy(() -> itemConfig.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void toStringHidesIdentifiers() {
    String text = entry().toString();

    assertThat(text).doesNotContain(ACCOUNT_REF).doesNotContain(POLICY_NO).contains("itemCount=2");
  }
}
