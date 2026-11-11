package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;

public final class PasswordValidationRequestParser {
  private static final String PASSWORD = "password";
  private static final String PURPOSE = "purpose";
  private static final String OPERATION_PAYLOAD = "operationPayload";
  private final JsonFactory factory =
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  private final PasswordValidationPurposePayloadDecoderRegistry decoders;
  private final ObjectMapper objectMapper;
  private final int passwordMinLength;
  private final int passwordMaxLength;

  public PasswordValidationRequestParser(
      PasswordValidationPurposePayloadDecoderRegistry decoders,
      ObjectMapper objectMapper,
      int min,
      int max) {
    if (min <= 0 || max < min) throw new IllegalArgumentException("Invalid login password bounds");
    this.decoders = Objects.requireNonNull(decoders, "decoders must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    passwordMinLength = min;
    passwordMaxLength = max;
  }

  public record Parsed(
      String password,
      PasswordValidationPurpose purpose,
      String operationPayloadJson,
      PasswordValidationPurposePayload payload) {
    public Parsed {
      Objects.requireNonNull(password, "password must not be null");
      Objects.requireNonNull(purpose, "purpose must not be null");
      Objects.requireNonNull(operationPayloadJson, "operationPayloadJson must not be null");
      Objects.requireNonNull(payload, "payload must not be null");
    }

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
        require(token != null, "PAGE");
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
            int start = (int) parser.currentTokenLocation().getCharOffset();
            parser.skipChildren();
            int end = (int) parser.currentLocation().getCharOffset();
            operation = raw.substring(start, end);
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
          PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE.name().equals(purpose)
              || PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE.name().equals(purpose)
              || PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE.name().equals(purpose),
          PURPOSE);
      var parsedPurpose = PasswordValidationPurpose.parse(purpose);
      require(operation != null, OPERATION_PAYLOAD);
      JsonNode operationNode = objectMapper.readTree(operation);
      var payload = decoders.decode(parsedPurpose, operationNode);
      return new Parsed(password, parsedPurpose, operation, payload);
    } catch (IOException | IllegalArgumentException exception) {
      // Do not retain parser diagnostics or causes containing request values.
      throw new PasswordValidationRequestInvalidException("PAGE");
    }
  }

  private static void require(boolean valid, String target) {
    if (!valid) throw new PasswordValidationRequestInvalidException(target);
  }
}
