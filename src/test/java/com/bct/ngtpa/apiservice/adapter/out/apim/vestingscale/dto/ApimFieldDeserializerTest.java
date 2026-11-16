package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApimFieldDeserializerTest {

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  @Test
  void preservesAbsentNullAndValueForScalarAndNestedFields() throws Exception {
    Probe absent = JSON.readValue("{}", Probe.class);
    Probe explicitNull =
        JSON.readValue(
            "{\"text\":null,\"integer\":null,\"decimal\":null,\"nested\":null,\"rows\":null}",
            Probe.class);
    Probe values =
        JSON.readValue(
            """
            {
              "text":"value",
              "integer":7,
              "decimal":10.25,
              "nested":{"label":"nested"},
              "rows":[{"label":"row"}]
            }
            """,
            Probe.class);

    assertThat(absent.text().state()).isEqualTo(ApimField.State.ABSENT);
    assertThat(absent.integer().state()).isEqualTo(ApimField.State.ABSENT);
    assertThat(absent.decimal().state()).isEqualTo(ApimField.State.ABSENT);
    assertThat(absent.nested().state()).isEqualTo(ApimField.State.ABSENT);
    assertThat(absent.rows().state()).isEqualTo(ApimField.State.ABSENT);
    assertThat(explicitNull.text().state()).isEqualTo(ApimField.State.NULL);
    assertThat(explicitNull.integer().state()).isEqualTo(ApimField.State.NULL);
    assertThat(explicitNull.decimal().state()).isEqualTo(ApimField.State.NULL);
    assertThat(explicitNull.nested().state()).isEqualTo(ApimField.State.NULL);
    assertThat(explicitNull.rows().state()).isEqualTo(ApimField.State.NULL);
    assertThat(values.text().value()).isEqualTo("value");
    assertThat(values.integer().value()).isEqualTo(7);
    assertThat(values.decimal().value()).isEqualByComparingTo("10.25");
    assertThat(values.nested().value()).isEqualTo(new Nested("nested"));
    assertThat(values.rows().value()).containsExactly(new Nested("row"));

    Probe integerDecimal = JSON.readValue("{\"decimal\":10}", Probe.class);
    assertThat(integerDecimal.decimal().value()).isEqualByComparingTo("10");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("wrongScalarKinds")
  void rejectsScalarCoercions(String json) {
    assertThatThrownBy(() -> JSON.readValue(json, Probe.class))
        .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
  }

  static Stream<Arguments> wrongScalarKinds() {
    return Stream.of(
        Arguments.of("{\"text\":123}"),
        Arguments.of("{\"text\":true}"),
        Arguments.of("{\"integer\":\"7\"}"),
        Arguments.of("{\"integer\":4.8}"),
        Arguments.of("{\"integer\":true}"),
        Arguments.of("{\"decimal\":\"10.0\"}"),
        Arguments.of("{\"decimal\":true}"));
  }

  private record Probe(
      ApimField<String> text,
      ApimField<Integer> integer,
      ApimField<BigDecimal> decimal,
      ApimField<Nested> nested,
      ApimField<List<Nested>> rows) {}

  private record Nested(String label) {}
}
