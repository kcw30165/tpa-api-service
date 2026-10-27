package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads the {@code fields} of a rule group ({@code when.groups[].fields}).
 *
 * <p>Spring Boot binds nested lists inside a {@code List<Map<String, Object>>} as index-keyed maps
 * ({@code {"0": "a", "1": "b"}}) rather than lists, so both shapes are accepted. Any other shape
 * reads as an empty list.
 */
public final class RuleGroupFields {

  private static final Pattern INDEX = Pattern.compile("\\d{1,9}");

  private RuleGroupFields() {}

  public static List<String> of(Object raw) {
    if (raw instanceof List<?> list) {
      return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }
    if (raw instanceof Map<?, ?> map && isIndexKeyed(map)) {
      return map.entrySet().stream()
          .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey().toString())))
          .map(Map.Entry::getValue)
          .filter(Objects::nonNull)
          .map(Object::toString)
          .toList();
    }
    return List.of();
  }

  private static boolean isIndexKeyed(Map<?, ?> map) {
    return !map.isEmpty()
        && map.keySet().stream()
            .allMatch(key -> key != null && INDEX.matcher(key.toString()).matches());
  }
}
