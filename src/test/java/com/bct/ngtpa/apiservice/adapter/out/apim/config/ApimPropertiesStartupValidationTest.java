package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ApimPropertiesStartupValidationTest {

    @Test
    void validateRoutingRunsDuringContextInitialization_and_failsOnInvalidRouting() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(ApimRoutingValidationConfig.class);

        runner.run(context -> {
            // default ApimProperties has no routing configured; context should fail to start
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
            Throwable t = context.getStartupFailure();
            boolean found = false;
            while (t != null) {
                if (t.getMessage() != null && t.getMessage().contains("apim.routing")) {
                    found = true;
                    break;
                }
                t = t.getCause();
            }
            assertThat(found).isTrue();
        });
    }
}
