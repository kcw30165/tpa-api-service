package com.bct.ngtpa.apiservice.infrastructure.environment;

import java.util.Locale;
import java.util.Objects;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Shared policy for resolving and classifying the current deployment environment.
 *
 * <p>The first active Spring profile remains the deployment-environment signal. Only PROD and DR
 * are production-like. Missing, blank, and unknown values are deliberately treated as
 * non-production-like.
 */
@Component
public class DeploymentEnvironmentPolicy {

  private static final String PROD = "PROD";
  private static final String DR = "DR";

  private final Environment environment;

  public DeploymentEnvironmentPolicy(Environment environment) {
    this.environment = Objects.requireNonNull(environment, "environment must not be null");
  }

  public boolean isProductionLikeDeployment() {
    return isProductionLikeDeploymentEnv(currentDeploymentEnv());
  }

  public boolean isNonProductionLikeDeployment() {
    return !isProductionLikeDeployment();
  }

  public boolean isProductionLikeDeploymentEnv(String deploymentEnv) {
    String normalized = normalize(deploymentEnv);
    return PROD.equals(normalized) || DR.equals(normalized);
  }

  public String currentDeploymentEnv() {
    String[] activeProfiles = environment.getActiveProfiles();
    return activeProfiles.length == 0 ? "" : activeProfiles[0];
  }

  private String normalize(String deploymentEnv) {
    return deploymentEnv == null ? "" : deploymentEnv.trim().toUpperCase(Locale.ROOT);
  }
}
