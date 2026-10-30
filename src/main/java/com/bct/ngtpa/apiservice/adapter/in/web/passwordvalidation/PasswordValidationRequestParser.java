package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import com.fasterxml.jackson.core.*;
import java.io.IOException;

public final class PasswordValidationRequestParser {
  private static final String PASSWORD = "password";
  private static final String PURPOSE = "purpose";
  private static final String OPERATION_PAYLOAD = "operationPayload";
  private final JsonFactory factory =
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  private final OperationPayloadBindingPort bindings;
  private final int passwordMinLength;
  private final int passwordMaxLength;

  public PasswordValidationRequestParser(OperationPayloadBindingPort bindings, int min, int max) {
    if (min <= 0 || max < min) throw new IllegalArgumentException("Invalid login password bounds");
    this.bindings = java.util.Objects.requireNonNull(bindings);
    passwordMinLength = min;
    passwordMaxLength = max;
  }

  public record Parsed(String password, PasswordValidationPurpose purpose, PayloadDigest binding) {
    @Override
    public String toString() {
      return "Parsed[<redacted>]";
    }
  }

  public Parsed parse(String raw) {
    require(raw != null, "PAGE");
    try (JsonParser parser = factory.createParser(raw)) {
      require(parser.nextToken() == JsonToken.START_OBJECT, "PAGE");
      String password = null;
      String purpose = null;
      String operation = null;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        require(parser.currentToken() == JsonToken.FIELD_NAME, "PAGE");
        String name = parser.currentName();
        JsonToken token = parser.nextToken();
        switch (name) {
          case PASSWORD -> {
            require(token == JsonToken.VALUE_STRING, PASSWORD);
            password = parser.getText();
          }
          case PURPOSE -> {
            require(token == JsonToken.VALUE_STRING, PURPOSE);
            purpose = parser.getText();
          }
          case OPERATION_PAYLOAD -> {
            require(token == JsonToken.START_OBJECT, OPERATION_PAYLOAD);
            int start = (int) parser.currentTokenLocation().getCharOffset();
            parser.skipChildren();
            operation = raw.substring(start, (int) parser.currentLocation().getCharOffset());
          }
          default -> throw new PasswordValidationRequestInvalidException("PAGE");
        }
      }
      require(parser.nextToken() == null, "PAGE");
      require(
          password != null
              && password.length() >= passwordMinLength
              && password.length() <= passwordMaxLength,
          PASSWORD);
      require(
          PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE.name().equals(purpose), PURPOSE);
      require(operation != null, OPERATION_PAYLOAD);
      validateOperation(operation);
      return new Parsed(
          password, PasswordValidationPurpose.parse(purpose), bindings.digest(operation));
    } catch (IOException | IllegalArgumentException exception) {
      // Do not retain parser diagnostics or causes containing request values.
      throw new PasswordValidationRequestInvalidException("PAGE");
    }
  }

  private void validateOperation(String raw) throws IOException {
    try (JsonParser parser = factory.createParser(raw)) {
      parser.nextToken();
      boolean formVersion = false;
      boolean applyToAll = false;
      boolean fields = false;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        String name = parser.currentName();
        JsonToken token = parser.nextToken();
        switch (name) {
          case "formVersion" -> {
            require(
                token == JsonToken.VALUE_STRING && !parser.getText().isBlank(), OPERATION_PAYLOAD);
            formVersion = true;
          }
          case "applyToAllAccounts" -> {
            require(
                token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE, OPERATION_PAYLOAD);
            applyToAll = true;
          }
          case "fields" -> {
            require(token == JsonToken.START_OBJECT, OPERATION_PAYLOAD);
            require(parser.nextToken() == JsonToken.FIELD_NAME, OPERATION_PAYLOAD);
            do {
              parser.nextToken();
              parser.skipChildren();
            } while (parser.nextToken() != JsonToken.END_OBJECT);
            fields = true;
          }
          default -> parser.skipChildren();
        }
      }
      require(formVersion && applyToAll && fields, OPERATION_PAYLOAD);
    }
  }

  private static void require(boolean valid, String target) {
    if (!valid) throw new PasswordValidationRequestInvalidException(target);
  }
}
