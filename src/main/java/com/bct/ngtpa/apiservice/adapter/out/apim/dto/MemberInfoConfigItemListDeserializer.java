package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MemberInfoConfigItemListDeserializer
    extends StdDeserializer<List<MemberInfoConfigItem>> {

  public MemberInfoConfigItemListDeserializer() {
    super(List.class);
  }

  @Override
  public List<MemberInfoConfigItem> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    JsonNode config = parser.getCodec().readTree(parser);
    if (!config.isArray()) {
      throw JsonMappingException.from(parser, "Expected a configuration matrix array.");
    }

    List<MemberInfoConfigItem> items = new ArrayList<>();
    for (JsonNode item : config) {
      if (isEligible(item)) {
        items.add(parser.getCodec().treeToValue(item, MemberInfoConfigItem.class));
      }
    }
    return items;
  }

  private static boolean isEligible(JsonNode item) {
    if (!item.isObject()) {
      return false;
    }
    JsonNode itemType = item.get("item-type");
    if (itemType == null || !itemType.isTextual()) {
      return false;
    }
    String normalizedType = itemType.textValue().strip();
    return normalizedType.equalsIgnoreCase("UI") || normalizedType.equalsIgnoreCase("DATA");
  }
}
