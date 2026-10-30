package com.bct.ngtpa.apiservice.adapter.out.passwordvalidation;

import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.TreeMap;

/** Version-one binary JSON encoding; both issuance and consumption must use the raw JSON port. */
public final class JsonOperationPayloadCanonicalizer implements OperationPayloadBindingPort {
  private static final int VERSION = 1;
  private final JsonFactory factory =
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  @Override
  public PayloadDigest digest(String operationPayloadJson) {
    if (operationPayloadJson == null) {
      throw invalidPayload();
    }
    try (JsonParser parser = factory.createParser(operationPayloadJson)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw invalidPayload();
      }
      var buffer = new ByteArrayOutputStream();
      var output = new DataOutputStream(buffer);
      output.writeInt(VERSION);
      output.write(value(parser));
      if (parser.nextToken() != null) {
        throw invalidPayload();
      }
      return new PayloadDigest(
          VERSION,
          HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray())));
    } catch (IOException | IllegalArgumentException exception) {
      // Parser exceptions can contain personal values. Do not retain them as causes.
      throw invalidPayload();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable");
    }
  }

  private byte[] value(JsonParser parser) throws IOException {
    return switch (parser.currentToken()) {
      case START_OBJECT -> writeObject(parser);
      case START_ARRAY -> writeArray(parser);
      case VALUE_STRING -> writeString(parser);
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> writeNumber(parser);
      case VALUE_TRUE -> taggedValue((byte) 't');
      case VALUE_FALSE -> taggedValue((byte) 'f');
      case VALUE_NULL -> taggedValue((byte) '0');
      default -> throw invalidPayload();
    };
  }

  private byte[] writeObject(JsonParser parser) throws IOException {
    var buffer = new ByteArrayOutputStream();
    var output = new DataOutputStream(buffer);
    var members = new TreeMap<String, byte[]>();
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      if (parser.currentToken() != JsonToken.FIELD_NAME) {
        throw invalidPayload();
      }
      String name = parser.currentName();
      if (parser.nextToken() == null) {
        throw invalidPayload();
      }
      members.put(name, value(parser));
    }
    output.writeByte('{');
    output.writeInt(members.size());
    for (var entry : members.entrySet()) {
      text(output, entry.getKey());
      output.write(entry.getValue());
    }
    return buffer.toByteArray();
  }

  private byte[] writeArray(JsonParser parser) throws IOException {
    var buffer = new ByteArrayOutputStream();
    var output = new DataOutputStream(buffer);
    var items = new ArrayList<byte[]>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      if (parser.currentToken() == null) {
        throw invalidPayload();
      }
      items.add(value(parser));
    }
    output.writeByte('[');
    output.writeInt(items.size());
    for (byte[] item : items) {
      output.write(item);
    }
    return buffer.toByteArray();
  }

  private byte[] writeString(JsonParser parser) throws IOException {
    var buffer = new ByteArrayOutputStream();
    var output = new DataOutputStream(buffer);
    output.writeByte('s');
    text(output, parser.getText());
    return buffer.toByteArray();
  }

  private byte[] writeNumber(JsonParser parser) throws IOException {
    var buffer = new ByteArrayOutputStream();
    var output = new DataOutputStream(buffer);
    output.writeByte('n');
    text(output, new BigDecimal(parser.getText()).stripTrailingZeros().toString());
    return buffer.toByteArray();
  }

  private static byte[] taggedValue(byte tag) throws IOException {
    var buffer = new ByteArrayOutputStream();
    var output = new DataOutputStream(buffer);
    output.writeByte(tag);
    return buffer.toByteArray();
  }

  private static void text(DataOutputStream output, String value) throws IOException {
    // UTF-16 code units preserve even lone JSON surrogate escapes without replacement collisions.
    output.writeInt(value.length());
    output.writeChars(value);
  }

  private static IllegalArgumentException invalidPayload() {
    return new IllegalArgumentException("Invalid operation payload");
  }
}
