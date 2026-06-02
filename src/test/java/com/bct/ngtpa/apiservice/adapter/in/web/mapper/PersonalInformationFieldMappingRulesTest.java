package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PersonalInformationFieldMappingRulesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
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
    void maps_apim_config_and_data_to_bff_fields_following_rules() {
        contextRunner.run(context -> {
            var pages = context.getBean(BffPagesProperties.class);
            assertNotNull(pages, "Expected BffPagesProperties to be bound from YAML");

            // Simulate APIM config list -> simplified map of item-id -> config-value
            Map<String, String> apimConfig = new LinkedHashMap<>();
            apimConfig.put("addr1", "EDITABLE_COM");
            apimConfig.put("business-phone", "READONLY");
            apimConfig.put("other-phone", "EDITABLE_OPTION");
            apimConfig.put("fax", "HIDDEN");
            apimConfig.put("country", "READONLY");
            apimConfig.put("unknown-item", "READONLY");

            Map<String, Object> apimData = new HashMap<>();
            apimData.put("addr1", "1 Example Street");
            apimData.put("business-phone", "2345 6789");
            apimData.put("other-phone", "+44 20 7946 0000");
            apimData.put("fax", "SECRET-FAX");
            apimData.put("country", "Local");

            var sanitizer = new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties());

            // Instantiate mapper directly (unit test of mapping algorithm)
            var mapper = new PersonalInformationFieldMapperImpl(sanitizer);

            Map<String, Object> mappedResult = mapper.map(apimData, apimConfig, pages);
            assertNotNull(mappedResult, "Expected non-null mapping result");

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> fields = (Map<String, Map<String, Object>>) mappedResult.get("fields");
            assertNotNull(fields, "Expected 'fields' map in mapping result");

            // HIDDEN -> omitted
            assertFalse(fields.containsKey("faxNo"), "HIDDEN fields must be omitted");

            // READONLY -> emitted, readonly=true, required=false
            assertTrue(fields.containsKey("hongKongBusinessPhone"));
            var business = fields.get("hongKongBusinessPhone");
            assertEquals("2345 6789", business.get("value"));
            assertEquals("2345 6789", business.get("originalValue"));
            assertEquals(Boolean.TRUE, business.get("readonly"));
            assertEquals(Boolean.FALSE, business.get("required"));

            // EDITABLE_COM -> emitted, readonly=false, required=true
            assertTrue(fields.containsKey("residentialAddressLine1"));
            var addr = fields.get("residentialAddressLine1");
            assertEquals("1 Example Street", addr.get("value"));
            assertEquals(Boolean.FALSE, addr.get("readonly"));
            assertEquals(Boolean.TRUE, addr.get("required"));

            // EDITABLE_OPTION -> emitted, readonly=false, required=false
            assertTrue(fields.containsKey("overseasPhoneNumber"));
            var other = fields.get("overseasPhoneNumber");
            assertEquals("+44 20 7946 0000", other.get("value"));
            assertEquals(Boolean.FALSE, other.get("readonly"));
            assertEquals(Boolean.FALSE, other.get("required"));

            // Country not converted
            assertTrue(fields.containsKey("residentialCountry"));
            var country = fields.get("residentialCountry");
            assertEquals("Local", country.get("value"));

            // OptionSource must be present but options are not included
            assertEquals("countries", country.get("optionSource"));

            // Java mapping exists but APIM did not return the APIM item-id -> omitted
            assertFalse(fields.containsKey("hongKongMobilePhone"));

            // Confirmation metadata included
            assertNotNull(mappedResult.get("confirmation"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}
