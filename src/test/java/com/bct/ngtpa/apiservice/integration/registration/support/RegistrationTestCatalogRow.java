package com.bct.ngtpa.apiservice.integration.registration.support;

import java.util.LinkedHashMap;
import java.util.Map;

public record RegistrationTestCatalogRow(Map<String, String> values) {

  public RegistrationTestCatalogRow {
    values = Map.copyOf(new LinkedHashMap<>(values));
  }

  public String testId() {
    return value("Test ID");
  }

  public String value(String column) {
    return values.getOrDefault(column, "");
  }
}
