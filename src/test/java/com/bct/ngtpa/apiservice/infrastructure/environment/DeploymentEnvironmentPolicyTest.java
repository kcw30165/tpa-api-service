package com.bct.ngtpa.apiservice.infrastructure.environment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class DeploymentEnvironmentPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"prod", "PROD", " prod ", "dr", "DR", " dr "})
    void productionLikeEnvironment_isRestrictedToProdAndDr(String deploymentEnv) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(deploymentEnv);
        var policy = new DeploymentEnvironmentPolicy(environment);

        assertThat(policy.isProductionLikeDeployment()).isTrue();
        assertThat(policy.isNonProductionLikeDeployment()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "local", "dev", "sit", "uat", "qa", "unknown"})
    void blankKnownAndUnknownNonProductionEnvironment_isNonProductionLike(String deploymentEnv) {
        var policy = new DeploymentEnvironmentPolicy(new MockEnvironment());

        assertThat(policy.isProductionLikeDeploymentEnv(deploymentEnv)).isFalse();
    }

    @Test
    void missingActiveProfile_isNonProductionLike() {
        var policy = new DeploymentEnvironmentPolicy(new MockEnvironment());

        assertThat(policy.currentDeploymentEnv()).isEmpty();
        assertThat(policy.isProductionLikeDeployment()).isFalse();
        assertThat(policy.isNonProductionLikeDeployment()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dr", "uat"})
    void firstActiveProfile_remainsTheDeploymentEnvironmentSignal(String firstProfile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(firstProfile, "prod");
        var policy = new DeploymentEnvironmentPolicy(environment);

        assertThat(policy.currentDeploymentEnv()).isEqualTo(firstProfile);
        assertThat(policy.isProductionLikeDeployment()).isEqualTo(!"uat".equals(firstProfile));
    }
}
