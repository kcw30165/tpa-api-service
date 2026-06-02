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

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
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
            @SuppressWarnings("unchecked")
            Map<String, Object> page = (Map<String, Object>) bound.getPages().get("sample-page");
            assertNotNull(page.get("metadata"));
            assertNotNull(page.get("form"));
            assertNotNull(page.get("validations"));
            assertNotNull(page.get("confirmation"));

            @SuppressWarnings("unchecked")
            Map<String, Object> form = (Map<String, Object>) page.get("form");
            assertNotNull(form.get("actions"));
            assertNotNull(form.get("sections"));

            List<Object> sections = asList(form.get("sections"));
            assertEquals(2, sections.size());

            // first section fields
            @SuppressWarnings("unchecked")
            Map<String, Object> section1 = (Map<String, Object>) sections.get(0);
            List<Object> fields1 = asList(section1.get("fields"));
            assertEquals(2, fields1.size());
        });
    }

    @Test
    void bindsAllWhenOperatorValues() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> page = (Map<String, Object>) bound.getPages().get("sample-page");
            Set<String> whenOps = collectOperatorValues(page, "when", "operator");
            Set<String> expected = new HashSet<>(Arrays.asList("notBlank", "blank", "changed", "all", "any", "allGroupsEmpty"));
            assertTrue(whenOps.containsAll(expected), "missing when.operator values: " + expected + " vs " + whenOps);
        });
    }

    @Test
    void bindsAllThenOperatorValues() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> page = (Map<String, Object>) bound.getPages().get("sample-page");
            Set<String> thenOps = collectOperatorValues(page, "then", "operator");
            Set<String> expected = new HashSet<>(Arrays.asList("fail", "required", "allRequired", "showMessage"));
            assertTrue(thenOps.containsAll(expected), "missing then.operator values: " + expected + " vs " + thenOps);
        });
    }

    @Test
    void bindsAllSeverityValues() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> page = (Map<String, Object>) bound.getPages().get("sample-page");
            Set<String> severities = collectScalarValues(page, "severity");
            Set<String> expected = new HashSet<>(Arrays.asList("error", "warning", "info"));
            assertTrue(severities.containsAll(expected), "missing severity values: " + expected + " vs " + severities);
        });
    }

    @Test
    void preservesFieldAndSectionDisplayOrder() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> page = (Map<String, Object>) bound.getPages().get("sample-page");
            @SuppressWarnings("unchecked")
            Map<String, Object> form = (Map<String, Object>) page.get("form");
            List<Object> sections = asList(form.get("sections"));
            assertEquals("section-1", ((Map<String, Object>) sections.get(0)).get("id"));
            assertEquals("section-2", ((Map<String, Object>) sections.get(1)).get("id"));

            List<Object> fields1 = asList(((Map<String, Object>) sections.get(0)).get("fields"));
            assertEquals("field-a", ((Map<String, Object>) fields1.get(0)).get("id"));
            assertEquals("field-b", ((Map<String, Object>) fields1.get(1)).get("id"));
        });
    }

    @Test
    void preservesLocalizedLabelsEnAndZhHk() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> page = (Map<String, Object>) bound.getPages().get("sample-page");
            @SuppressWarnings("unchecked")
            Map<String, Object> form = (Map<String, Object>) page.get("form");
            List<Object> sections = asList(form.get("sections"));
            @SuppressWarnings("unchecked")
            Map<String, Object> firstSection = (Map<String, Object>) sections.get(0);
            List<Object> fields = asList(firstSection.get("fields"));
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldA = (Map<String, Object>) fields.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> label = (Map<String, Object>) fieldA.get("label");
            assertTrue(label.containsKey("en"));
            assertTrue(label.containsKey("zh_HK"));
        });

    }

    private static List<Object> asList(Object obj) {
        if (obj == null) {
            return Collections.emptyList();
        }
        if (obj instanceof List) {
            return (List<Object>) obj;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            // Map keys may be numeric strings produced by binder ("0","1",...)
            List<String> keys = new ArrayList<>();
            for (Object k : map.keySet()) {
                keys.add(k.toString());
            }
            keys.sort(Comparator.comparingInt(Integer::parseInt));
            List<Object> result = new ArrayList<>();
            for (String k : keys) {
                result.add(map.get(k));
            }
            return result;
        }
        return Collections.emptyList();
    }

    private Set<String> collectOperatorValues(Map<String, Object> page, String containerKey, String operatorKey) {
        Set<String> result = new HashSet<>();
        // field-level validations
            @SuppressWarnings("unchecked")
            Map<String, Object> form = (Map<String, Object>) page.get("form");
            if (form != null) {
                List<Object> sections = asList(form.get("sections"));
                if (sections != null) {
                    for (Object s : sections) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> section = (Map<String, Object>) s;
                        List<Object> fields = asList(section.get("fields"));
                        if (fields != null) {
                            for (Object f : fields) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> field = (Map<String, Object>) f;
                                List<Object> validations = asList(field.get("validations"));
                                if (validations != null) {
                                    for (Object v : validations) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> val = (Map<String, Object>) v;
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> container = (Map<String, Object>) val.get(containerKey);
                                        if (container != null && container.get(operatorKey) != null) {
                                            result.add(container.get(operatorKey).toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        // page-level validations
        List<Object> pageValidations = asList(page.get("validations"));
        if (pageValidations != null) {
            for (Object pv : pageValidations) {
                @SuppressWarnings("unchecked")
                Map<String, Object> val = (Map<String, Object>) pv;
                @SuppressWarnings("unchecked")
                Map<String, Object> container = (Map<String, Object>) val.get(containerKey);
                if (container != null && container.get(operatorKey) != null) {
                    result.add(container.get(operatorKey).toString());
                }
            }
        }
        return result;
    }

    private Set<String> collectScalarValues(Map<String, Object> page, String scalarKey) {
        Set<String> result = new HashSet<>();
        // field-level validations
            @SuppressWarnings("unchecked")
            Map<String, Object> form = (Map<String, Object>) page.get("form");
            if (form != null) {
                List<Object> sections = asList(form.get("sections"));
                if (sections != null) {
                    for (Object s : sections) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> section = (Map<String, Object>) s;
                        List<Object> fields = asList(section.get("fields"));
                        if (fields != null) {
                            for (Object f : fields) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> field = (Map<String, Object>) f;
                                List<Object> validations = asList(field.get("validations"));
                                if (validations != null) {
                                    for (Object v : validations) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> val = (Map<String, Object>) v;
                                        if (val.get(scalarKey) != null) {
                                            result.add(val.get(scalarKey).toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        // page-level validations
        List<Object> pageValidations = asList(page.get("validations"));
        if (pageValidations != null) {
            for (Object pv : pageValidations) {
                @SuppressWarnings("unchecked")
                Map<String, Object> val = (Map<String, Object>) pv;
                if (val.get(scalarKey) != null) {
                    result.add(val.get(scalarKey).toString());
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
