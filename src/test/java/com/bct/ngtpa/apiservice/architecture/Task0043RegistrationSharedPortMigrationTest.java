package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043RegistrationSharedPortMigrationTest {

    @Test
    void registrationTechnicalPortsAreTypedCompatibilityAliasesOfSharedContracts() {
        assertThat(AuthFlowOtpPort.class).isAssignableFrom(RegistrationOtpPort.class);
        assertThat(AuthFlowMessagePort.class).isAssignableFrom(RegistrationMessagePort.class);
        assertThat(AuthFlowIdempotencyPort.class).isAssignableFrom(RegistrationIdempotencyPort.class);
        assertThat(AuthFlowProtectedContextPort.class)
                .isAssignableFrom(RegistrationAuthFlowContextProtectorPort.class);
    }

    @Test
    void temporaryOutboundCompatibilityAdaptersAreRemovedAfterAliasMigration() {
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/compatibility")))
                .isFalse();
    }
}
