package com.bct.ngtpa.apiservice.adapter.out.configserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Branch-oriented tests for {@link ReferenceDateResolver}. */
class ReferenceDateResolverTest {

  private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private ReferenceDateResolver newResolver() {
    return new ReferenceDateResolver(clock);
  }

  @Test
  void publicConstructorUsesSystemClock() {
    // Exercises the public no-arg constructor for coverage; result is
    // simply today's date via the system clock, no fixed assertion needed.
    assertThat(new ReferenceDateResolver().resolve(null, null, null, null)).isNotNull();
  }

  // ----- production-like deployment envs bypass overrides ------------------------

  @Test
  void nullDeploymentEnvIsTreatedAsProductionLike() {
    LocalDate result = newResolver().resolve("env1", null, "01/01/2020", "UTC");
    assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void blankDeploymentEnvIsTreatedAsProductionLike() {
    LocalDate result = newResolver().resolve("env1", "  ", "01/01/2020", "UTC");
    assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void prodDeploymentEnvIgnoresOverride() {
    LocalDate result = newResolver().resolve("env1", "PROD", "01/01/2020", "UTC");
    assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void prdDeploymentEnvIgnoresOverride() {
    LocalDate result = newResolver().resolve("env1", "prd", "01/01/2020", "UTC");
    assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void drDeploymentEnvIgnoresOverride() {
    LocalDate result = newResolver().resolve("env1", "dr", "01/01/2020", "UTC");
    assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  // ----- non-production envs: no override provided --------------------------------

  @Test
  void nonProdWithBothOverridesBlankUsesServerDate() {
    LocalDate result = newResolver().resolve("env1", "SIT", null, null);
    assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  // ----- partial override: exactly one of the two provided -------------------------

  @Test
  void onlyOverrideDateProvidedThrows() {
    assertThatThrownBy(() -> newResolver().resolve("env1", "SIT", "01/01/2020", null))
        .isInstanceOf(InvalidContributionRequestException.class);
  }

  @Test
  void onlyOverrideZoneIdProvidedThrows() {
    assertThatThrownBy(() -> newResolver().resolve("env1", "SIT", null, "UTC"))
        .isInstanceOf(InvalidContributionRequestException.class);
  }

  // ----- both overrides provided: validation + parsing -------------------------------

  @Test
  void invalidZoneIdThrows() {
    assertThatThrownBy(() -> newResolver().resolve("env1", "SIT", "01/01/2020", "Not/AZone"))
        .isInstanceOf(InvalidContributionRequestException.class);
  }

  @Test
  void invalidDateFormatThrows() {
    assertThatThrownBy(() -> newResolver().resolve("env1", "SIT", "2020-01-01", "UTC"))
        .isInstanceOf(InvalidContributionRequestException.class);
  }

  @Test
  void validOverrideParsesSuccessfully() {
    LocalDate result = newResolver().resolve("env1", "SIT", "25/12/2025", "UTC");
    assertThat(result).isEqualTo(LocalDate.of(2025, 12, 25));
  }
}
