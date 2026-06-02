package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BffPagesPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withInitializer(context -> {
                        var resource = new ClassPathResource("pageconfig/bff-pages-binding-test.yml");
                        try {
                            var propertySources = new YamlPropertySourceLoader().load("bffPagesTest", resource);
                            propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException("Failed to load YAML", ex);
                        }
                    });

    @Test
    void bindsCommonAndPages() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            assertNotNull(bound);
            assertNotNull(bound.getCommon());
            assertNotNull(bound.getPages());
            assertTrue(bound.getPages().containsKey("sample-page"));
        });
    }

    @Test
    void bindsOnePageWithMetadataFormActionsSectionsFieldsValidationsConfirmation() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("sample-page");
            assertNotNull(page.getMetadata());
            assertNotNull(page.getForm());
            assertNotNull(page.getValidations());
            assertNotNull(page.getConfirmation());

            FormMetadataProperties form = page.getForm();
            assertNotNull(form.getActions());
            assertNotNull(form.getSections());

            List<SectionProperties> sections = form.getSections();
            assertEquals(2, sections.size());

            // first section fields
            SectionProperties section1 = sections.get(0);
            List<FieldProperties> fields1 = section1.getFields();
            assertEquals(2, fields1.size());
        });
    }

    @Test
    void bindsAllWhenOperatorValues() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("sample-page");
            Set<String> whenOps = collectWhenOperatorValues(page);
            Set<String> expected = new HashSet<>(
                    Arrays.asList("notBlank", "blank", "changed", "all", "any", "allGroupsEmpty"));
            assertTrue(whenOps.containsAll(expected), "missing when.operator values: " + expected + " vs " + whenOps);
        });
    }

    @Test
    void bindsAllThenOperatorValues() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("sample-page");
            Set<String> thenOps = collectThenOperatorValues(page);
            Set<String> expected = new HashSet<>(Arrays.asList("fail", "required", "allRequired", "showMessage"));
            assertTrue(thenOps.containsAll(expected), "missing then.operator values: " + expected + " vs " + thenOps);
        });
    }

    @Test
    void bindsAllSeverityValues() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("sample-page");
            Set<String> severities = collectSeverityValues(page);
            Set<String> expected = new HashSet<>(Arrays.asList("error", "warning", "info"));
            assertTrue(severities.containsAll(expected), "missing severity values: " + expected + " vs " + severities);
        });
    }

    @Test
    void preservesFieldAndSectionDisplayOrder() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("sample-page");
            FormMetadataProperties form = page.getForm();
            List<SectionProperties> sections = form.getSections();
            assertEquals("section-1", sections.get(0).getId());
            assertEquals("section-2", sections.get(1).getId());

            List<FieldProperties> fields1 = sections.get(0).getFields();
            assertEquals("field-a", fields1.get(0).getId());
            assertEquals("field-b", fields1.get(1).getId());
        });
    }

    @Test
    void preservesLocalizedLabelsEnAndZhHk() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("sample-page");
            SectionProperties firstSection = page.getForm().getSections().get(0);
            FieldProperties fieldA = firstSection.getFields().get(0);
            Map<String, String> label = fieldA.getLabel();
            assertTrue(label.containsKey("en"));
            assertTrue(label.containsKey("zh_HK"));
        });

    }

    private Set<String> collectWhenOperatorValues(PageSchemaProperties page) {
        Set<String> result = new HashSet<>();
        if (page.getForm() != null && page.getForm().getSections() != null) {
            for (SectionProperties s : page.getForm().getSections()) {
                if (s.getFields() != null) {
                    for (FieldProperties f : s.getFields()) {
                        if (f.getValidations() != null) {
                            for (ValidationRuleProperties v : f.getValidations()) {
                                    if (v.getWhen() != null && v.getWhen().getOperator() != null) {
                                    result.add(v.getWhen().getOperator());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (page.getValidations() != null) {
            for (ValidationRuleProperties v : page.getValidations()) {
                if (v.getWhen() != null && v.getWhen().getOperator() != null) {
                    result.add(v.getWhen().getOperator());
                }
            }
        }
        return result;
    }

    private Set<String> collectThenOperatorValues(PageSchemaProperties page) {
        Set<String> result = new HashSet<>();
        if (page.getForm() != null && page.getForm().getSections() != null) {
            for (SectionProperties s : page.getForm().getSections()) {
                if (s.getFields() != null) {
                    for (FieldProperties f : s.getFields()) {
                        if (f.getValidations() != null) {
                            for (ValidationRuleProperties v : f.getValidations()) {
                                    if (v.getThen() != null && v.getThen().getOperator() != null) {
                                    result.add(v.getThen().getOperator());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (page.getValidations() != null) {
            for (ValidationRuleProperties v : page.getValidations()) {
                if (v.getThen() != null && v.getThen().getOperator() != null) {
                    result.add(v.getThen().getOperator());
                }
            }
        }
        return result;
    }

    private Set<String> collectSeverityValues(PageSchemaProperties page) {
        Set<String> result = new HashSet<>();
        if (page.getForm() != null && page.getForm().getSections() != null) {
            for (SectionProperties s : page.getForm().getSections()) {
                if (s.getFields() != null) {
                    for (FieldProperties f : s.getFields()) {
                        if (f.getValidations() != null) {
                            for (ValidationRuleProperties v : f.getValidations()) {
                                if (v.getSeverity() != null) {
                                    result.add(v.getSeverity());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (page.getValidations() != null) {
            for (ValidationRuleProperties v : page.getValidations()) {
                if (v.getSeverity() != null) {
                    result.add(v.getSeverity());
                }
            }
        }
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}
