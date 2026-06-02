package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Failing tests for the Personal Information APIM adapter (TRPGetMemberInfo).
 *
 * These tests assert the desired outbound port/contract and behaviour; they will fail until
 * an adapter implementing {@link ApimMemberInfoPort} is provided.
 */
class ApimPersonalInformationAdapterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void apim_member_info_adapter_should_be_registered_as_a_bean() {
        contextRunner.run(context -> {
            assertDoesNotThrow(() -> context.getBean(ApimMemberInfoPort.class),
                    "Expected an ApimMemberInfoPort bean to be registered (adapter not implemented yet)");
        });
    }

    @Test
    void apim_member_info_adapter_should_send_post_and_return_parsed_result() {
        contextRunner.run(context -> {
            var port = context.getBean(ApimMemberInfoPort.class);

            // Example command that should be encrypted by the adapter before sending
            FetchMemberInfoCommand cmd = new FetchMemberInfoCommand("JP", "encrypted-policy", "encrypted-cert", "encrypted-user");

            // When implemented, this should perform POST to /ws/NGTPA/v1/TRPGetMemberInfo and return parsed payload
            MemberInfoResult result = port.fetchMemberInfo(cmd).block();
            // Expectations verified by later adapter implementation tests
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
    }
}
