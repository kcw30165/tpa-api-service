package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Sub-task 1.4 guard: after moving every exception mapping into per-capability {@code
 * *ExceptionMappings} components, {@code @ExceptionHandler} must remain registered only on {@code
 * ApiExceptionHandler} (the single {@code @RestControllerAdvice}). This guards against a future
 * change reintroducing scattered {@code @ExceptionHandler} methods elsewhere under {@code
 * adapter.in.web}.
 */
class ExceptionMappingRegistryArchitectureTest {

  private static final Path MAIN_JAVA = Path.of("src/main/java");
  private static final Path WEB_PACKAGE =
      MAIN_JAVA.resolve("com/bct/ngtpa/apiservice/adapter/in/web");
  private static final String ALLOWED_FILE_NAME = "ApiExceptionHandler.java";

  @Test
  void exceptionHandlerAnnotationOnlyAppearsInApiExceptionHandler() throws IOException {
    List<Path> javaFiles = javaFiles(WEB_PACKAGE);
    assertThat(javaFiles).isNotEmpty();

    for (Path file : javaFiles) {
      String source = Files.readString(file);
      if (!source.contains("@ExceptionHandler")) {
        continue;
      }
      assertThat(file.getFileName().toString())
          .as("@ExceptionHandler found outside ApiExceptionHandler in %s", file)
          .isEqualTo(ALLOWED_FILE_NAME);
    }
  }

  @Test
  void apiExceptionHandlerIsStillTheOnlyRestControllerAdviceUnderWebPackage() throws IOException {
    List<Path> javaFiles = javaFiles(WEB_PACKAGE);

    for (Path file : javaFiles) {
      String source = Files.readString(file);
      if (!source.contains("@RestControllerAdvice") && !source.contains("@ControllerAdvice")) {
        continue;
      }
      assertThat(file.getFileName().toString()).isEqualTo(ALLOWED_FILE_NAME);
    }
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> stream = Files.walk(root)) {
      return stream.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }
}
