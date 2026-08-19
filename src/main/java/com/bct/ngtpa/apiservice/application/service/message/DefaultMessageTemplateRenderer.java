package com.bct.ngtpa.apiservice.application.service.message;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DefaultMessageTemplateRenderer implements MessageTemplateRenderer {

  private static final Pattern PLACEHOLDER_PATTERN =
      Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_-]*)}");

  @Override
  public MessageTemplateRenderResult render(MessageTemplateRenderCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    String template = command.template();
    if (template == null || template.isBlank()) {
      throw new IllegalArgumentException("template must not be blank");
    }

    Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
    StringBuilder rendered = new StringBuilder(template.length());
    Map<String, String> variables = command.templateVariables();

    while (matcher.find()) {
      String variableName = matcher.group(1);
      String value = variables.get(variableName);
      if (value == null) {
        throw new IllegalArgumentException("Missing template variable: " + variableName);
      }
      matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
    }

    matcher.appendTail(rendered);
    return new MessageTemplateRenderResult(rendered.toString());
  }
}
