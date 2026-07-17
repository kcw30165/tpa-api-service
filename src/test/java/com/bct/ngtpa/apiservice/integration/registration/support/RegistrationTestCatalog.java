package com.bct.ngtpa.apiservice.integration.registration.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RegistrationTestCatalog {

    public static final Path DEFAULT_PATH = Path.of(
            "src/test/resources/integration/registration/integration_testing_plan.csv");

    private final List<String> headers;
    private final List<RegistrationTestCatalogRow> rows;
    private final Map<String, RegistrationTestCatalogRow> byId;

    private RegistrationTestCatalog(
            List<String> headers,
            List<RegistrationTestCatalogRow> rows,
            Map<String, RegistrationTestCatalogRow> byId) {
        this.headers = List.copyOf(headers);
        this.rows = List.copyOf(rows);
        this.byId = Map.copyOf(byId);
    }

    public static RegistrationTestCatalog loadDefault() throws IOException {
        return load(DEFAULT_PATH);
    }

    public static RegistrationTestCatalog load(Path path) throws IOException {
        List<List<String>> csv = RegistrationCsv.read(path);
        if (csv.isEmpty()) {
            throw new IOException("Registration integration catalogue is empty: " + path);
        }
        List<String> headers = csv.getFirst();
        if (!headers.contains("Test ID") || !headers.contains("Actual Result")
                || !headers.contains("Status") || !headers.contains("Reason")) {
            throw new IOException("Registration integration catalogue is missing required columns: " + path);
        }

        List<RegistrationTestCatalogRow> rows = new ArrayList<>();
        Map<String, RegistrationTestCatalogRow> byId = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex < csv.size(); rowIndex++) {
            List<String> cells = csv.get(rowIndex);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), column < cells.size() ? cells.get(column) : "");
            }
            RegistrationTestCatalogRow row = new RegistrationTestCatalogRow(values);
            if (row.testId().isBlank()) {
                throw new IOException("Blank Test ID at CSV row " + (rowIndex + 1));
            }
            if (byId.putIfAbsent(row.testId(), row) != null) {
                throw new IOException("Duplicate Test ID: " + row.testId());
            }
            rows.add(row);
        }
        return new RegistrationTestCatalog(headers, rows, byId);
    }

    public List<String> headers() {
        return headers;
    }

    public List<RegistrationTestCatalogRow> rows() {
        return rows;
    }

    public Set<String> testIds() {
        return new LinkedHashSet<>(byId.keySet());
    }

    public RegistrationTestCatalogRow require(String testId) {
        RegistrationTestCatalogRow row = byId.get(testId);
        if (row == null) {
            throw new IllegalArgumentException("Unknown registration integration Test ID: " + testId);
        }
        return row;
    }
}
