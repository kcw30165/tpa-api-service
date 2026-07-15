package com.bct.ngtpa.apiservice.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class RegistrationDownstreamErrorClassificationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("registration-pages",
                            new ClassPathResource("application-page-registration.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            });

    @Test void dottedDownstreamCodesBindAsExactExplicitValues() {
        runner.run(context -> {
            var pages = context.getBean(BffPagesProperties.class).getPages();
            assertThat(pages.get("registration-start").getDownstreamErrorClassification()
                    .getExplicitMappings()).extracting(
                            com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.DownstreamErrorClassificationMappingProperties::getDownstreamErrorCode)
                    .containsExactly("err.tpa.member.not.found", "err.tpa.hkid.invalid");
            assertThat(pages.get("registration-password-setup").getDownstreamErrorClassification()
                    .getExplicitMappings()).extracting(
                            com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.DownstreamErrorClassificationMappingProperties::getDownstreamErrorCode)
                    .containsExactly("err.tpa.member.not.found", "err.tpa.cust.id.duplicated", "err.tpa.member.registered");
        });
    }

    @Test void searchKnownErrorsMapToOnePublicVerificationFailure() {
        runner.run(context -> {
            var adapter=adapter(context);
            for(String code:java.util.List.of("err.tpa.member.not.found","err.tpa.hkid.invalid")) {
                var result=adapter.classify(request("registration-start","TRPSearchUnregisteredMember",code));
                assertThat(result.responseCode()).isEqualTo("err.registration.start.verificationFailed");
                assertThat(result.httpStatus()).isEqualTo(422);
                assertThat(result.fallbackUsed()).isFalse();
            }
        });
    }

    @Test void usernameCheckHasNoInventedEndpointSpecificApimMapping() {
        runner.run(context -> {
            var result=adapter(context).classify(request("registration-username-check","TRPChkCustId","err.tpa.any.generic.error"));
            assertThat(result.responseCode()).isEqualTo("err.workflow.downstream.businessRejected");
            assertThat(result.httpStatus()).isEqualTo(422);
            assertThat(result.fallbackUsed()).isTrue();
        });
    }

    @Test void registrationCompletionMapsThreeConfirmedTasErrors() {
        runner.run(context -> {
            var adapter=adapter(context);
            assertMapping(adapter,"err.tpa.member.not.found","err.registration.complete.memberNotFound","PAGE");
            assertMapping(adapter,"err.tpa.cust.id.duplicated","err.registration.username.isRegistered","username");
            assertMapping(adapter,"err.tpa.member.registered","err.registration.complete.memberAlreadyRegistered","PAGE");
        });
    }

    @Test void unknownCompletionBusinessErrorUsesConfiguredFallback() {
        runner.run(context -> {
            var result=adapter(context).classify(request("registration-password-setup","TRPRegMember","err.tpa.other"));
            assertThat(result.responseCode()).isEqualTo("err.workflow.downstream.businessRejected");
            assertThat(result.httpStatus()).isEqualTo(422);
            assertThat(result.fallbackUsed()).isTrue();
        });
    }

    private void assertMapping(ConfigBackedDownstreamErrorClassificationAdapter adapter,String downstream,String response,String target) {
        var result=adapter.classify(request("registration-password-setup","TRPRegMember",downstream));
        assertThat(result.responseCode()).isEqualTo(response);
        assertThat(result.httpStatus()).isEqualTo(422);
        assertThat(result.target()).isEqualTo(target);
        assertThat(result.fallbackUsed()).isFalse();
    }
    private ConfigBackedDownstreamErrorClassificationAdapter adapter(org.springframework.context.ApplicationContext context) {
        return new ConfigBackedDownstreamErrorClassificationAdapter(context.getBean(BffPagesProperties.class));
    }
    private DownstreamErrorClassificationRequest request(String page,String endpoint,String code) {
        return new DownstreamErrorClassificationRequest(page,"APIM_TAS",endpoint,code);
    }
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {}
}
