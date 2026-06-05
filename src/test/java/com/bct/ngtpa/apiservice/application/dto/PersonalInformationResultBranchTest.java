package com.bct.ngtpa.apiservice.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationResultBranchTest {

    @Test
    void nullMapsDefaultToImmutableEmptyMaps() {
        var result = new PersonalInformationResult(null, null, null);

        assertThat(result.data()).isEmpty();
        assertThat(result.config()).isEmpty();
        assertThat(result.configItems()).isEmpty();
        assertThatThrownBy(() -> result.data().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nonNullMapsAreDefensivelyCopiedAndImmutable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", "a@example.test");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("email", "READONLY");
        Map<String, MemberInfoConfigItem> configItems = new LinkedHashMap<>();
        configItems.put("email", MemberInfoConfigItem.of("email", MemberInfoConfigItemType.DATA, "READONLY"));

        var result = new PersonalInformationResult(data, config, configItems);
        data.put("email", "changed@example.test");
        config.put("email", "HIDDEN");
        configItems.clear();

        assertThat(result.data()).containsEntry("email", "a@example.test");
        assertThat(result.config()).containsEntry("email", "READONLY");
        assertThat(result.configItems()).containsKey("email");
        assertThatThrownBy(() -> result.config().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void legacyConstructorDefaultsConfigItemsToEmptyMap() {
        var result = new PersonalInformationResult(Map.of("addr1", "value"), Map.of("addr1", "EDITABLE_COM"));

        assertThat(result.data()).containsEntry("addr1", "value");
        assertThat(result.config()).containsEntry("addr1", "EDITABLE_COM");
        assertThat(result.configItems()).isEmpty();
    }
}
