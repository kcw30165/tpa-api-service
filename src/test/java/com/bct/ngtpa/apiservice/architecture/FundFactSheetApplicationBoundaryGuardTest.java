package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FundFactSheetApplicationBoundaryGuardTest {

  private static final String MAIN = "src/main/java/com/bct/ngtpa/apiservice/";

  private static final List<String> APPLICATION_FILES =
      List.of(
          "application/dto/fundfactsheet/GetFundFactSheetCommand.java",
          "application/dto/fundfactsheet/FundFactSheetResult.java",
          "application/dto/fundfactsheet/FundFactSheetRowResult.java",
          "application/port/in/GetFundFactSheetUseCase.java",
          "application/port/out/FundFactSheetPresentationEventPort.java",
          "application/usecase/fundfactsheet/FundFactSheetNormalizer.java",
          "application/usecase/fundfactsheet/GetFundFactSheetService.java");

  @Test
  void presentationEventPortHasExactlyTheFourEventsAndNoMethodTakesAnArgument() {
    Method[] methods =
        Arrays.stream(FundFactSheetPresentationEventPort.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .toArray(Method[]::new);

    assertThat(methods).hasSize(4);
    assertThat(Arrays.stream(methods).map(Method::getName).toList())
        .containsExactlyInAnyOrder(
            "missingSeq", "duplicateSeq", "blankEnglishName", "malformedFactsheet");
    for (Method method : methods) {
      assertThat(method.getParameterCount()).as(method.getName()).isZero();
      assertThat(method.getReturnType()).as(method.getName()).isEqualTo(void.class);
    }
  }

  @Test
  void loggerWritesOnlyBareStringLiteralsWithNoPlaceholderOrArgument() throws Exception {
    String source =
        Files.readString(
            Path.of(MAIN + "infrastructure/logging/FundFactSheetPresentationEventLogger.java"));

    assertThat(source).doesNotContain("{}");
    assertThat(count(source, "log\\.[a-z]+\\(")).isEqualTo(4);
    assertThat(count(source, "log\\.warn\\(\"event=fund-fact-sheet\\.[a-z-]+\"\\);")).isEqualTo(4);
  }

  @Test
  void applicationFilesAreFrameworkFreeNonBlockingAndSilent() throws Exception {
    for (String file : APPLICATION_FILES) {
      String source = Files.readString(Path.of(MAIN + file));
      assertThat(source)
          .as(file)
          .doesNotContain("org.springframework", "org.slf4j", "Slf4j", "Logger", "System.out")
          .doesNotContain(".block(", ".subscribe(", "apiservice.adapter", "apiservice.config");
    }
  }

  @Test
  void useCaseAndNormalizerExposeNoExecuteMethodThatTheExecutionAspectCouldAdvise()
      throws Exception {
    for (String file :
        List.of(
            "application/usecase/fundfactsheet/GetFundFactSheetService.java",
            "application/usecase/fundfactsheet/FundFactSheetNormalizer.java",
            "application/port/in/GetFundFactSheetUseCase.java")) {
      assertThat(Files.readString(Path.of(MAIN + file))).as(file).doesNotContain(" execute(");
    }
  }

  private static int count(String text, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(text);
    int n = 0;
    while (matcher.find()) {
      n++;
    }
    return n;
  }
}
