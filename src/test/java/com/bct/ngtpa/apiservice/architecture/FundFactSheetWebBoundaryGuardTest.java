package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.FundFactSheetController;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class FundFactSheetWebBoundaryGuardTest {

  private static final String MAIN = "src/main/java/com/bct/ngtpa/apiservice/";
  private static final String WEB = MAIN + "adapter/in/web/fundfactsheet/";

  private static final List<String> WEB_FILES =
      List.of(
          WEB + "FundFactSheetController.java",
          WEB + "mapper/FundFactSheetWebMapper.java",
          WEB + "response/FundFactSheetResponse.java",
          WEB + "response/FundFactSheetResultResponse.java",
          WEB + "response/FundFactSheetRowResponse.java");

  private static final List<Class<? extends Annotation>> MAPPINGS =
      List.of(
          GetMapping.class,
          PostMapping.class,
          PutMapping.class,
          PatchMapping.class,
          DeleteMapping.class,
          RequestMapping.class);

  @Test
  void theControllerExposesExactlyOneGetRouteWithNoRequestParametersOtherThanTheLanguageHeader() {
    List<Method> routes =
        Arrays.stream(FundFactSheetController.class.getDeclaredMethods())
            .filter(method -> MAPPINGS.stream().anyMatch(method::isAnnotationPresent))
            .toList();

    assertThat(routes).hasSize(1);
    Method route = routes.get(0);
    GetMapping get = route.getAnnotation(GetMapping.class);
    assertThat(get).isNotNull();
    String[] paths = get.path().length > 0 ? get.path() : get.value();
    assertThat(paths).containsExactly("/fund-fact-sheet");
    assertThat(get.produces()).containsExactly("application/json");
    RequestMapping root = FundFactSheetController.class.getAnnotation(RequestMapping.class);
    String[] roots = root.path().length > 0 ? root.path() : root.value();
    assertThat(roots).containsExactly("/api/v1");

    assertThat(route.getParameterCount()).isEqualTo(1);
    RequestHeader header = route.getParameters()[0].getAnnotation(RequestHeader.class);
    assertThat(header).isNotNull();
    String headerName = header.name().isEmpty() ? header.value() : header.name();
    assertThat(headerName).isEqualTo("Accept-Language");
    assertThat(header.required()).isFalse();
  }

  @Test
  void theControllerIsARestControllerInItsFeaturePackageAndNotInTheOldFlatFolder() {
    assertThat(FundFactSheetController.class.isAnnotationPresent(RestController.class)).isTrue();
    assertThat(FundFactSheetController.class.isAnnotationPresent(ControllerAdvice.class)).isFalse();
    assertThat(FundFactSheetController.class.getPackageName())
        .isEqualTo("com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet");
    Path oldFlat = Path.of(MAIN + "adapter/in/web/controller/FundFactSheetController.java");
    assertThat(Files.exists(oldFlat)).isFalse();
  }

  @Test
  void theControllerHoldsNoAdapterOrInfrastructureDependency() {
    List<String> forbidden =
        List.of(".adapter.out.", ".redis.", ".apim.", ".persistence.", "WebClient");

    for (Field field : FundFactSheetController.class.getDeclaredFields()) {
      String type = field.getType().getName();
      assertThat(forbidden.stream().noneMatch(type::contains)).as(type).isTrue();
    }
  }

  @Test
  void theWebSourcesNeverBlockLogResolveMessagesOrExposeDownstreamStatuses() throws Exception {
    for (String file : WEB_FILES) {
      String source = Files.readString(Path.of(file));
      assertThat(source)
          .as(file)
          .doesNotContain("ApiStatus.DOWNSTREAM_ERROR", "ApiStatus.DOWNSTREAM_REJECTED")
          .doesNotContain(".block(", ".subscribe(", "System.out", "org.slf4j", "Slf4j")
          .doesNotContain("ErrorMessageResolver", "resolvePublicMessage", "PortalAccessContextPort")
          .doesNotContain("currentOrEmpty()", "adapter.out", "application.usecase");
    }
  }

  @Test
  void theMapperSortsWithLocaleRootAndNeverWithACollator() throws Exception {
    String mapper = Files.readString(Path.of(WEB + "mapper/FundFactSheetWebMapper.java"));

    assertThat(mapper)
        .containsIgnoringWhitespaces("toLowerCase(Locale.ROOT)")
        .containsIgnoringWhitespaces("RequestLanguageResolver.isZhHk(")
        .doesNotContain("Collator", "DynamicDescriptionResolver", "SortEngine");
  }
}
