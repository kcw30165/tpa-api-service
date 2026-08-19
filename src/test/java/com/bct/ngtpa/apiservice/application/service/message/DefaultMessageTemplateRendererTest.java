package com.bct.ngtpa.apiservice.application.service.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultMessageTemplateRendererTest {

  private final MessageTemplateRenderer renderer = new DefaultMessageTemplateRenderer();

  @Test
  void rendersSinglePlaceholder() {
    assertThat(render("Hello {name}", Map.of("name", "Member"))).isEqualTo("Hello Member");
  }

  @Test
  void rendersMultiplePlaceholders() {
    assertThat(
            render(
                "Hello {name}, reference {refNo} is ready.",
                Map.of("name", "Member", "refNo", "ABC123")))
        .isEqualTo("Hello Member, reference ABC123 is ready.");
  }

  @Test
  void rendersRepeatedPlaceholder() {
    assertThat(render("{refNo}-{refNo}", Map.of("refNo", "ABC123"))).isEqualTo("ABC123-ABC123");
  }

  @Test
  void allowsExtraVariables() {
    assertThat(render("Reference {refNo}", Map.of("refNo", "ABC123", "unused", "ignored")))
        .isEqualTo("Reference ABC123");
  }

  @Test
  void rejectsMissingVariableSafely() {
    assertThatThrownBy(() -> render("Hello {name}", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name")
        .hasMessageNotContaining("Hello {name}")
        .hasMessageNotContaining("Member");
  }

  @Test
  void rejectsNullVariableValueAsMissing() {
    Map<String, String> variables = new HashMap<>();
    variables.put("name", null);

    assertThatThrownBy(() -> render("Hello {name}", variables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name")
        .hasMessageNotContaining("Hello {name}");
  }

  @Test
  void rejectsBlankTemplate() {
    assertThatThrownBy(() -> render(null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template");

    assertThatThrownBy(() -> render("   ", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template");
  }

  @Test
  void templateWithoutPlaceholdersReturnsOriginalText() {
    assertThat(render("Static message", Map.of())).isEqualTo("Static message");
  }

  @Test
  void rendererDoesNotAppendUnits() {
    String rendered = render("Valid for {ttlValue}", Map.of("ttlValue", "5"));

    assertThat(rendered).isEqualTo("Valid for 5");
    assertThat(rendered).isNotEqualTo("Valid for 5 minutes");
  }

  @Test
  void unitTextCanComeFromTemplateLiteral() {
    assertThat(render("Valid for {ttlValue} minutes", Map.of("ttlValue", "5")))
        .isEqualTo("Valid for 5 minutes");
  }

  @Test
  void unitTextCanComeFromVariable() {
    assertThat(
            render("Valid for {ttlValue} {ttlUnit}", Map.of("ttlValue", "2", "ttlUnit", "hours")))
        .isEqualTo("Valid for 2 hours");
  }

  @Test
  void localizedUnitTextCanComeFromVariable() {
    assertThat(render("有效期為 {ttlValue}{ttlUnit}", Map.of("ttlValue", "5", "ttlUnit", "分鐘")))
        .isEqualTo("有效期為 5分鐘");
  }

  private String render(String template, Map<String, String> variables) {
    return renderer.render(new MessageTemplateRenderCommand(template, variables)).renderedText();
  }
}
