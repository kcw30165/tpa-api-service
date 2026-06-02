package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PersonalInformationPageYamlBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void personalInformationPage_shouldBeDeclaredUnderBffPages() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            assertNotNull(bound);
            var pages = bound.getPages();
            assertNotNull(pages);
            // Expected to fail until application-page-personal-information.yml is added
            assertTrue(pages.containsKey("personalInformation"), "Expected bff-pages.pages to contain 'personalInformation' page key");
        });
    }

    @Test
    void personalInformationPage_shouldHaveExpectedMetadataFormSectionsAndFields() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            var pages = bound.getPages();
            assertNotNull(pages);

            // This will fail because the page YAML does not exist yet
            assertTrue(pages.containsKey("personalInformation"), "Expected page key 'personalInformation' to be present");

            PageSchemaProperties page = pages.get("personalInformation");
            assertNotNull(page.getMetadata(), "Expected metadata");
            assertEquals("personalInformationPage", page.getMetadata().getId(), "Expected metadata.id");
            assertNotNull(page.getForm(), "Expected form");
            assertEquals("personalInformationForm", page.getForm().getId(), "Expected form.id");

            List<SectionProperties> sections = page.getForm().getSections();
            assertNotNull(sections, "Expected sections");
            List<String> expectedSections = List.of(
                    "addressInformation",
                    "localContactNumber",
                    "overseasContacts",
                    "electronicContacts",
                    "communicationSettings",
                    "importantNotes",
                    "controlInformation");
            for (String expected : expectedSections) {
                assertTrue(sections.stream().anyMatch(s -> expected.equals(s.getId())), "Missing section " + expected);
            }

            boolean hasField = sections.stream()
                    .flatMap(s -> s.getFields() == null ? Stream.empty() : s.getFields().stream())
                    .anyMatch(f -> "overseasPhoneExtension".equals(f.getId()));
            assertTrue(hasField, "Expected field 'overseasPhoneExtension' to be present in sections");

            // Actions and confirmation expectations
            assertNotNull(page.getForm().getActions(), "Expected actions");
            List<String> expectedActions = List.of("edit", "submit", "reset", "cancel");
            for (String action : expectedActions) {
                assertTrue(page.getForm().getActions().stream().anyMatch(a -> action.equals(a.getName())), "Expected action " + action);
            }

            assertNotNull(page.getConfirmation(), "Expected confirmation metadata in page schema");
        });
    }

    @Test
    void bindingModel_shouldSupportOptionSourceAndVersionAndDefaultMode() {
        // Reflection-based checks for properties that the page YAML is expected to use.
        // These assertions will fail if the binding model does not yet expose the named properties.
        assertDoesNotThrow(() -> FieldProperties.class.getDeclaredMethod("getOptionSource"));
        assertDoesNotThrow(() -> PageMetadataProperties.class.getDeclaredMethod("getVersion"));
        assertDoesNotThrow(() -> FormMetadataProperties.class.getDeclaredMethod("getDefaultMode"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}
