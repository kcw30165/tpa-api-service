package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every APIM response DTO, and every repository type nested inside one, must ignore unknown
 * properties. TAS adds response fields without notice; the production ObjectMapper keeps
 * FAIL_ON_UNKNOWN_PROPERTIES enabled, so a single unannotated nested DTO turns an additive field
 * into a failed exchange (TRPGetMemberAccBal fundDtl.chin-fund-fact-sheet, 2026-10-13).
 *
 * <p>Response types are the second {@code .class} argument of each {@code new ApimExchangeRequest}
 * call in adapter/out/apim. Nested types are found by reflection over record components and
 * instance fields, including generic arguments, so types declared inside another file are covered.
 */
class ApimResponseDtoIgnoreUnknownArchitectureTest {

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path APIM = MAIN.resolve("com/bct/ngtpa/apiservice/adapter/out/apim");
  private static final String APIM_PACKAGE = "com.bct.ngtpa.apiservice.adapter.out.apim";
  private static final int MIN_CALL_SITES = 22;
  private static final Pattern EXCHANGE =
      Pattern.compile("new\\s+ApimExchangeRequest\\s*<[^>]*>\\s*\\(");
  private static final Pattern CLASS_LITERAL = Pattern.compile("\\b([A-Z]\\w*)\\.class\\b");

  @Test
  void everyReachableApimResponseDtoIgnoresUnknownProperties() throws Exception {
    Map<String, List<String>> fqnsBySimpleName = new HashMap<>();
    List<Path> sources = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(APIM)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
    }
    for (Path source : sources) {
      String fqn =
          MAIN.relativize(source)
              .toString()
              .replace('\\', '/')
              .replace('/', '.')
              .replace(".java", "");
      String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
      fqnsBySimpleName.computeIfAbsent(simple, k -> new ArrayList<>()).add(fqn);
    }

    Set<Class<?>> roots = new LinkedHashSet<>();
    List<String> unresolved = new ArrayList<>();
    int callSites = 0;
    for (Path source : sources) {
      String text = Files.readString(source);
      String pkg = source.getParent().toString().replace('\\', '/');
      Matcher exchange = EXCHANGE.matcher(text);
      while (exchange.find()) {
        callSites++;
        String window =
            text.substring(exchange.end(), Math.min(text.length(), exchange.end() + 1500));
        Matcher literal = CLASS_LITERAL.matcher(window);
        String request = literal.find() ? literal.group(1) : null;
        String response = literal.find() ? literal.group(1) : null;
        Class<?> type = response == null ? null : resolve(response, text, pkg, fqnsBySimpleName);
        if (request == null || type == null) {
          unresolved.add(source + " -> " + response);
        } else {
          roots.add(type);
        }
      }
    }
    assertThat(callSites)
        .as("ApimExchangeRequest call sites")
        .isGreaterThanOrEqualTo(MIN_CALL_SITES);
    assertThat(unresolved).as("unresolved response types").isEmpty();

    List<String> missing = new ArrayList<>();
    Set<Class<?>> seen = new LinkedHashSet<>();
    Deque<Class<?>> queue = new ArrayDeque<>(roots);
    while (!queue.isEmpty()) {
      Class<?> type = queue.pop();
      if (!seen.add(type)) {
        continue;
      }
      JsonIgnoreProperties annotation = type.getAnnotation(JsonIgnoreProperties.class);
      if (annotation == null || !annotation.ignoreUnknown()) {
        missing.add(type.getName());
      }
      for (Type member : memberTypes(type)) {
        collect(member, queue);
      }
    }
    assertThat(seen).as("reachable APIM response DTOs").hasSizeGreaterThanOrEqualTo(MIN_CALL_SITES);
    assertThat(missing).as("APIM response DTOs without ignoreUnknown = true").isEmpty();
  }

  private static Class<?> resolve(
      String simple, String text, String pkgPath, Map<String, List<String>> fqnsBySimpleName) {
    List<String> candidates = fqnsBySimpleName.getOrDefault(simple, List.of());
    String chosen = null;
    for (String fqn : candidates) {
      if (text.contains("import " + fqn + ";")) {
        chosen = fqn;
      }
    }
    if (chosen == null) {
      for (String fqn : candidates) {
        String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
        if (text.contains("import " + pkg + ".*;") || pkgPath.endsWith(pkg.replace('.', '/'))) {
          chosen = fqn;
        }
      }
    }
    if (chosen == null && candidates.size() == 1) {
      chosen = candidates.get(0);
    }
    try {
      return chosen == null ? null : Class.forName(chosen);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static List<Type> memberTypes(Class<?> type) {
    List<Type> types = new ArrayList<>();
    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        types.add(component.getGenericType());
      }
      return types;
    }
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
          types.add(field.getGenericType());
        }
      }
    }
    return types;
  }

  private static void collect(Type type, Deque<Class<?>> queue) {
    if (type instanceof Class<?> raw) {
      Class<?> element = raw.isArray() ? raw.getComponentType() : raw;
      if (element.getName().startsWith(APIM_PACKAGE)
          && !element.isEnum()
          && !element.isInterface()
          && !element.isPrimitive()) {
        queue.add(element);
      }
    } else if (type instanceof ParameterizedType parameterized) {
      collect(parameterized.getRawType(), queue);
      for (Type argument : parameterized.getActualTypeArguments()) {
        collect(argument, queue);
      }
    } else if (type instanceof GenericArrayType array) {
      collect(array.getGenericComponentType(), queue);
    } else if (type instanceof WildcardType wildcard) {
      for (Type bound : wildcard.getUpperBounds()) {
        collect(bound, queue);
      }
    }
  }
}
