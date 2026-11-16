package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

@Component
@RequiredArgsConstructor
@Slf4j
public final class ApplicationRoutesLogger {

  private static final String UNKNOWN = "unknown";

  private final ObjectProvider<RequestMappingHandlerMapping> mappingsProvider;
  private final ObjectMapper objectMapper;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    RouteSnapshot snapshot;
    try {
      List<RequestMappingHandlerMapping> handlerMappings =
          mappingsProvider.orderedStream().toList();
      if (handlerMappings.isEmpty()) {
        snapshot = RouteSnapshot.unknown();
      } else {
        List<RequestMappingInfo> registeredMappings =
            handlerMappings.stream()
                .flatMap(handlerMapping -> handlerMapping.getHandlerMethods().keySet().stream())
                .toList();
        snapshot = new RouteSnapshot(registeredMappings.size(), routePatterns(registeredMappings));
      }
    } catch (BeansException exception) {
      snapshot = RouteSnapshot.unknown();
    }

    Map<String, Object> countEvent = new LinkedHashMap<>();
    countEvent.put("event", "app.routes");
    countEvent.put("registeredMappings", snapshot.loggedCount());
    countEvent.put("routes", snapshot.loggedRouteCount());
    log.info("{}", toJson(countEvent));

    if (log.isDebugEnabled() && snapshot.registeredMappings() != null) {
      Map<String, Object> detailEvent = new LinkedHashMap<>();
      detailEvent.put("event", "app.routes.detail");
      detailEvent.put("mappings", snapshot.mappings());
      log.debug("{}", toJson(detailEvent));
    }
  }

  private static List<RoutePattern> routePatterns(List<RequestMappingInfo> mappings) {
    return mappings.stream()
        .flatMap(ApplicationRoutesLogger::routePatterns)
        .distinct()
        .sorted(Comparator.comparing(RoutePattern::method).thenComparing(RoutePattern::path))
        .toList();
  }

  private static java.util.stream.Stream<RoutePattern> routePatterns(RequestMappingInfo mapping) {
    Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
    List<String> methodNames =
        methods.isEmpty() ? List.of("ANY") : methods.stream().map(Enum::name).sorted().toList();
    Set<PathPattern> pathPatterns = mapping.getPatternsCondition().getPatterns();
    List<String> paths =
        pathPatterns.isEmpty()
            ? List.of("/")
            : pathPatterns.stream().map(PathPattern::getPatternString).sorted().toList();
    return methodNames.stream()
        .flatMap(method -> paths.stream().map(path -> new RoutePattern(method, path)));
  }

  private String toJson(Map<String, Object> event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize app.routes event", exception);
    }
  }

  private record RoutePattern(String method, String path) {}

  private record RouteSnapshot(Integer registeredMappings, List<RoutePattern> mappings) {
    private static RouteSnapshot unknown() {
      return new RouteSnapshot(null, List.of());
    }

    private Object loggedCount() {
      return registeredMappings == null ? UNKNOWN : registeredMappings;
    }

    private Object loggedRouteCount() {
      return registeredMappings == null ? UNKNOWN : mappings.size();
    }
  }
}
