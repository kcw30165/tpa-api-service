package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowOtpPort;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationSharedCoreMigrationArchitectureTest {

    @Test
    void existingGenericCoresMustBeTheSharedAuthFlowContracts() {
        assertThat(AuthFlowOtpPort.class).isAssignableFrom(OtpChallengePort.class);
        assertThat(AuthFlowMessagePort.class).isAssignableFrom(MessageDeliveryPort.class);
    }

    @Test
    void temporaryDelegatingCompatibilityAdaptersMustBeRemoved() {
        assertThat(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/authflow/DelegatingAuthFlowOtpAdapter.java"))
                .doesNotExist();
        assertThat(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/authflow/DelegatingAuthFlowMessageAdapter.java"))
                .doesNotExist();
    }

    @Test
    void registrationWiringUsesTheExistingSharedCoresWithoutDuplicateBeans() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/config/RegistrationStartGatewayConfig.java"));
        assertThat(source).contains(
                "OtpChallengePort otpChallengePort",
                "MessageDeliveryPort messageDeliveryPort");
        assertThat(source).doesNotContain(
                "AuthFlowOtpPort otpChallengePort",
                "AuthFlowMessagePort messageDeliveryPort");
    }
}
