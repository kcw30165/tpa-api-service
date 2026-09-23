package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Fail-first: ConfigServicePropertySourceFactory does not exist yet. */
class ConfigServicePropertySourceFactoryTest {

  @Test
  void mapsEntriesOntoPropertyNames() {
    var source =
        ConfigServicePropertySourceFactory.create(
            List.of(
                new ConfigServiceBootstrapEntry("fund-price.export.headers.fund-name", "Fund Name"),
                new ConfigServiceBootstrapEntry(
                    "fund-price.export.headers.chinese-fund-name", "\u57fa\u91d1\u540d\u7a31")));

    assertThat(source.getProperty("fund-price.export.headers.fund-name")).isEqualTo("Fund Name");
    assertThat(source.getProperty("fund-price.export.headers.chinese-fund-name"))
        .isEqualTo("\u57fa\u91d1\u540d\u7a31");
  }

  @Test
  void ignoresEntriesWithBlankKeys() {
    var source =
        ConfigServicePropertySourceFactory.create(
            List.of(
                new ConfigServiceBootstrapEntry("   ", "orphan"),
                new ConfigServiceBootstrapEntry("kept.key", "kept")));

    assertThat(source.getPropertyNames()).containsExactly("kept.key");
  }

  @Test
  void preservesAnEmptyValueRatherThanDroppingTheKey() {
    var source =
        ConfigServicePropertySourceFactory.create(
            List.of(new ConfigServiceBootstrapEntry("blank.value.key", "")));

    assertThat(source.getPropertyNames()).containsExactly("blank.value.key");
    assertThat(source.getProperty("blank.value.key")).isEqualTo("");
  }

  @Test
  void lastEntryWinsOnDuplicateKeys() {
    var source =
        ConfigServicePropertySourceFactory.create(
            List.of(
                new ConfigServiceBootstrapEntry("dup.key", "first"),
                new ConfigServiceBootstrapEntry("dup.key", "second")));

    assertThat(source.getProperty("dup.key")).isEqualTo("second");
  }

  @Test
  void emptyEntryListProducesAnEmptySource() {
    assertThat(ConfigServicePropertySourceFactory.create(List.of()).getPropertyNames()).isEmpty();
  }

  @Test
  void sourceIsNamedForDiagnosis() {
    assertThat(ConfigServicePropertySourceFactory.create(List.of()).getName())
        .isEqualTo(ConfigServicePropertySourceFactory.SOURCE_NAME);
  }
}
