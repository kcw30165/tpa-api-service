package com.bct.ngtpa.apiservice.integration.registration.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RegistrationTestResultStore {

  public static final Path OUTPUT =
      Path.of("target/registration-integration-evidence/registration-test-results.csv");
  private static final Map<String, RegistrationTestResult> RESULTS = new ConcurrentHashMap<>();

  private RegistrationTestResultStore() {}

  public static void record(String testId, RegistrationTestResult result) {
    RESULTS.put(testId, result);
  }

  public static synchronized void write(RegistrationTestCatalog catalog) throws IOException {
    Files.createDirectories(OUTPUT.getParent());
    List<String> lines = new ArrayList<>();
    lines.add(RegistrationCsv.encode(catalog.headers()));
    for (RegistrationTestCatalogRow row : catalog.rows()) {
      RegistrationTestResult result = RESULTS.get(row.testId());
      Map<String, String> values = new LinkedHashMap<>(row.values());
      if (result != null) {
        values.put("Actual Result", result.actualResult());
        values.put("Status", result.status().name().replace('_', ' '));
        values.put("Reason", result.reason());
        if (catalog.headers().contains("Evidence")) {
          values.put("Evidence", result.evidence());
        }
      } else {
        values.put("Actual Result", "");
        values.put("Status", "NOT RUN");
        values.put("Reason", "");
      }
      lines.add(
          RegistrationCsv.encode(
              catalog.headers().stream().map(header -> values.getOrDefault(header, "")).toList()));
    }
    writeAtomicallyOrToFallback(lines);
  }

  private static void writeAtomicallyOrToFallback(List<String> lines) throws IOException {
    String runSuffix = ProcessHandle.current().pid() + "-" + System.currentTimeMillis();
    Path temporary = OUTPUT.resolveSibling(OUTPUT.getFileName() + ".tmp-" + runSuffix);
    Files.write(
        temporary,
        lines,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    try {
      try {
        Files.move(
            temporary, OUTPUT, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException atomicMoveFailure) {
        Files.move(temporary, OUTPUT, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException lockedCanonicalOutput) {
      Path fallback = OUTPUT.resolveSibling("registration-test-results-" + runSuffix + ".csv");
      Files.move(temporary, fallback, StandardCopyOption.REPLACE_EXISTING);
      System.err.println(
          "[WARN] Canonical registration result CSV is locked; " + "wrote this run to " + fallback);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
