package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import com.fasterxml.jackson.core.*;
import java.io.IOException;
import java.util.HashSet;

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
          PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE.name().equals(purpose)
              || PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE.name().equals(purpose),
          PURPOSE);
      var parsedPurpose = PasswordValidationPurpose.parse(purpose);
      require(operation != null, OPERATION_PAYLOAD);
      validateOperation(operation, parsedPurpose);
      return new Parsed(password, parsedPurpose, bindings.digest(operation));
    } catch (IOException | IllegalArgumentException exception) {
      // Do not retain parser diagnostics or causes containing request values.
      throw new PasswordValidationRequestInvalidException("PAGE");
    }
  }

  private void validateOperation(String raw, PasswordValidationPurpose purpose) throws IOException {
    switch (purpose) {
      case PERSONAL_INFORMATION_UPDATE -> validatePersonalInformationOperation(raw);
      case CHANGE_INVESTMENT_CHOICE -> validateChangeInvestmentChoiceOperation(raw);
    }
  }

  private void validatePersonalInformationOperation(String raw) throws IOException {
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

  private void validateChangeInvestmentChoiceOperation(String raw) throws IOException {
    try (JsonParser parser = factory.createParser(raw)) {
      require(parser.nextToken() == JsonToken.START_OBJECT, OPERATION_PAYLOAD);
      boolean switchSources = false;
      boolean allocations = false;
      int percentageTotal = 0;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        require(parser.currentToken() == JsonToken.FIELD_NAME, OPERATION_PAYLOAD);
        String name = parser.currentName();
        JsonToken token = parser.nextToken();
        switch (name) {
          case "switchSources" -> {
            validateSwitchSources(parser, token);
            switchSources = true;
          }
          case "allocations" -> {
            percentageTotal = validateAllocations(parser, token);
            allocations = true;
          }
          default -> throw new PasswordValidationRequestInvalidException(OPERATION_PAYLOAD);
        }
      }
      require(parser.nextToken() == null, OPERATION_PAYLOAD);
      require(switchSources && allocations && percentageTotal == 100, OPERATION_PAYLOAD);
    }
  }

  private void validateSwitchSources(JsonParser parser, JsonToken token) throws IOException {
    require(token == JsonToken.START_ARRAY, OPERATION_PAYLOAD);
    var sources = new HashSet<String>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      require(parser.currentToken() == JsonToken.VALUE_STRING, OPERATION_PAYLOAD);
      String source = parser.getText();
      require(!source.isBlank() && sources.add(source), OPERATION_PAYLOAD);
    }
    require(!sources.isEmpty(), OPERATION_PAYLOAD);
  }

  private int validateAllocations(JsonParser parser, JsonToken token) throws IOException {
    require(token == JsonToken.START_ARRAY, OPERATION_PAYLOAD);
    var fundCodes = new HashSet<String>();
    int total = 0;
    int count = 0;
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      require(parser.currentToken() == JsonToken.START_OBJECT, OPERATION_PAYLOAD);
      boolean fundCodePresent = false;
      boolean percentagePresent = false;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        require(parser.currentToken() == JsonToken.FIELD_NAME, OPERATION_PAYLOAD);
        String name = parser.currentName();
        JsonToken value = parser.nextToken();
        switch (name) {
          case "fundCode" -> {
            require(value == JsonToken.VALUE_STRING, OPERATION_PAYLOAD);
            String fundCode = parser.getText();
            require(!fundCode.isBlank() && fundCodes.add(fundCode), OPERATION_PAYLOAD);
            fundCodePresent = true;
          }
          case "percentage" -> {
            require(value == JsonToken.VALUE_NUMBER_INT, OPERATION_PAYLOAD);
            int percentage = parser.getIntValue();
            require(percentage >= 1 && percentage <= 100, OPERATION_PAYLOAD);
            total += percentage;
            require(total <= 100, OPERATION_PAYLOAD);
            percentagePresent = true;
          }
          default -> throw new PasswordValidationRequestInvalidException(OPERATION_PAYLOAD);
        }
      }
      require(fundCodePresent && percentagePresent, OPERATION_PAYLOAD);
      count++;
    }
    require(count > 0, OPERATION_PAYLOAD);
    return total;
  }

  private static void require(boolean valid, String target) {
    if (!valid) throw new PasswordValidationRequestInvalidException(target);
  }
}
