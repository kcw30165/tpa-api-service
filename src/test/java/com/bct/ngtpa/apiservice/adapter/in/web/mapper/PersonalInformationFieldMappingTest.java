package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PersonalInformationFieldMappingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withInitializer(context -> {
                        var resource = new ClassPathResource("application-page-personal-information.yml");
                        try {
                            var propertySources = new YamlPropertySourceLoader().load("personalInformationPage", resource);
                            propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException("Failed to load YAML", ex);
                        }
                    });

    @Test
    void mapperBean_shouldBePresent_and_performMapping() {
        contextRunner.run(context -> {
            // Ensure page binding exists
            var pages = context.getBean(BffPagesProperties.class);

            // Build example APIM config and data fixtures
            Map<String, String> apimConfig = new HashMap<>();
            apimConfig.put("addr1", "EDITABLE_COM");
            apimConfig.put("business-phone", "READONLY");
            apimConfig.put("other-phone", "EDITABLE_OPTION");
            apimConfig.put("fax", "HIDDEN");

            Map<String, Object> apimData = new HashMap<>();
            apimData.put("addr1", "1 Example Street");
            apimData.put("business-phone", "2345 6789");
            apimData.put("other-phone", "+44 20 7946 0000");

            // Expectation: a PersonalInformationFieldMapper bean should exist and be callable.
            // This test will currently fail because no implementation bean is registered.
            assertDoesNotThrow(() -> context.getBean(PersonalInformationFieldMapper.class),
                    "Expected PersonalInformationFieldMapper bean to be present (not implemented yet)");

            // If a bean were present, it would be invoked like this (unreachable until implemented):
            // var mapper = context.getBean(PersonalInformationFieldMapper.class);
            // var mapped = mapper.map(apimData, apimConfig, pages);
            // assertEquals("1 Example Street", mapped.get("residentialAddressLine1").get("value"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}
