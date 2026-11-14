package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.math.BigDecimal;

public final class ApimFieldDeserializer extends JsonDeserializer<ApimField<?>>
    implements ContextualDeserializer {

  private final JavaType valueType;
  private final JsonDeserializer<?> valueDeserializer;

  public ApimFieldDeserializer() {
    this(null, null);
  }

  private ApimFieldDeserializer(JavaType valueType, JsonDeserializer<?> valueDeserializer) {
    this.valueType = valueType;
    this.valueDeserializer = valueDeserializer;
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
      throws JsonMappingException {
    JavaType wrapperType = property == null ? context.getContextualType() : property.getType();
    if (wrapperType == null || wrapperType.containedTypeCount() != 1) {
      throw JsonMappingException.from(context, "ApimField requires one value type");
    }

    JavaType valueType = wrapperType.containedType(0);
    JsonDeserializer<?> valueDeserializer =
        isStrictScalar(valueType)
            ? null
            : context.findContextualValueDeserializer(valueType, property);
    return new ApimFieldDeserializer(valueType, valueDeserializer);
  }

  @Override
  public ApimField<?> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    if (valueType == null) {
      throw JsonMappingException.from(parser, "ApimField deserializer is not contextualized");
    }

    if (valueType.hasRawClass(String.class)) {
      if (parser.currentToken() != JsonToken.VALUE_STRING) {
        throw mismatch(parser, "Expected JSON string");
      }
      return ApimField.value(parser.getText());
    }
    if (valueType.hasRawClass(Integer.class)) {
      if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
        throw mismatch(parser, "Expected JSON integer");
      }
      return ApimField.value(parser.getIntValue());
    }
    if (valueType.hasRawClass(BigDecimal.class)) {
      if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
          && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) {
        throw mismatch(parser, "Expected JSON number");
      }
      return ApimField.value(parser.getDecimalValue());
    }
    if (valueType.isCollectionLikeType() || valueType.isArrayType()) {
      if (parser.currentToken() != JsonToken.START_ARRAY) {
        throw mismatch(parser, "Expected JSON array");
      }
    } else if (parser.currentToken() != JsonToken.START_OBJECT) {
      throw mismatch(parser, "Expected JSON object");
    }
    return ApimField.value(valueDeserializer.deserialize(parser, context));
  }

  @Override
  public ApimField<?> getNullValue(DeserializationContext context) {
    return ApimField.nullValue();
  }

  @Override
  public ApimField<?> getAbsentValue(DeserializationContext context) {
    return ApimField.absent();
  }

  private MismatchedInputException mismatch(JsonParser parser, String message) {
    return MismatchedInputException.from(parser, valueType, message);
  }

  private static boolean isStrictScalar(JavaType valueType) {
    return valueType.hasRawClass(String.class)
        || valueType.hasRawClass(Integer.class)
        || valueType.hasRawClass(BigDecimal.class);
  }
}
