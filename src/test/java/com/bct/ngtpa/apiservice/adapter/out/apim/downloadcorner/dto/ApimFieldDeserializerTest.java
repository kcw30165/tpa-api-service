package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimFieldDeserializerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesStrictScalarsAndObjectAndArrayValues() throws Exception {
    var fields =
        objectMapper.readValue(
            """
            {"text":"value","integer":3,"decimal":2.5,"values":["a","b"],
             "nested":{"value":"nested"}}
            """,
            TestFields.class);

    assertThat(fields.text().value()).isEqualTo("value");
    assertThat(fields.integer().value()).isEqualTo(3);
    assertThat(fields.decimal().value()).isEqualByComparingTo("2.5");
    assertThat(fields.values().value()).containsExactly("a", "b");
    assertThat(fields.nested().value().value()).isEqualTo("nested");
  }

  @Test
  void distinguishesAbsentAndNullValues() throws Exception {
    var fields =
        objectMapper.readValue(
            """
            {"text":null,"integer":null,"decimal":null,"values":null,"nested":null}
            """,
            TestFields.class);

    assertThat(fields.text().state()).isEqualTo(ApimField.State.NULL);
    assertThat(fields.integer().state()).isEqualTo(ApimField.State.NULL);
    assertThat(fields.decimal().state()).isEqualTo(ApimField.State.NULL);
    assertThat(fields.values().state()).isEqualTo(ApimField.State.NULL);
    assertThat(fields.nested().state()).isEqualTo(ApimField.State.NULL);

    var absent = objectMapper.readValue("{}", TestFields.class);
    assertThat(absent.text().state()).isEqualTo(ApimField.State.ABSENT);
    assertThat(absent.values().state()).isEqualTo(ApimField.State.ABSENT);
  }

  @Test
  void deserializesRootFieldUsingItsContextualType() throws Exception {
    var field = objectMapper.readValue("\"root value\"", new TypeReference<ApimField<String>>() {});

    assertThat(field.state()).isEqualTo(ApimField.State.VALUE);
    assertThat(field.value()).isEqualTo("root value");
  }

  @Test
  void deserializesArrayFieldValues() throws Exception {
    var fields = objectMapper.readValue("{\"values\":[\"a\",\"b\"]}", ArrayFields.class);

    assertThat(fields.values().value()).containsExactly("a", "b");
  }

  @Test
  void rejectsApimFieldWithoutAValueType() {
    assertThatThrownBy(() -> objectMapper.readValue("{\"value\":\"text\"}", RawFields.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void rejectsDirectDeserializationWithoutContextualization() throws Exception {
    try (var parser = objectMapper.createParser("\"text\"")) {
      parser.nextToken();
      var context =
          ((DefaultDeserializationContext) objectMapper.getDeserializationContext())
              .createInstance(
                  objectMapper.getDeserializationConfig(),
                  parser,
                  objectMapper.getInjectableValues());

      assertThatThrownBy(() -> new ApimFieldDeserializer().deserialize(parser, context))
          .isInstanceOf(JsonProcessingException.class)
          .hasMessageContaining("not contextualized");
    }
  }

  @Test
  void rejectsUnexpectedScalarAndContainerJsonKinds() {
    assertThatThrownBy(() -> objectMapper.readValue("{\"text\":2}", TestFields.class))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> objectMapper.readValue("{\"integer\":2.1}", TestFields.class))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> objectMapper.readValue("{\"decimal\":\"2.1\"}", TestFields.class))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> objectMapper.readValue("{\"values\":{}}", TestFields.class))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> objectMapper.readValue("{\"nested\":[]}", TestFields.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  private record TestFields(
      ApimField<String> text,
      ApimField<Integer> integer,
      ApimField<BigDecimal> decimal,
      ApimField<List<String>> values,
      ApimField<NestedValue> nested) {}

  private record ArrayFields(ApimField<String[]> values) {}

  @SuppressWarnings("rawtypes")
  private record RawFields(ApimField value) {}

  private record NestedValue(String value) {}
}
