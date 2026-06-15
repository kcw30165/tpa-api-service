package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PersonalInformationDuplicateValidationRuleContractTest {

    private static final Path YAML = Path.of("src/main/resources/application-page-personal-information.yml");

    @Test
    void pageLevelValidationRulesDoNotContainDuplicateSemanticRequiredRules() throws Exception {
        String yaml = Files.readString(YAML);
        List<Rule> rules = extractPageLevelValidationRules(yaml);

        Map<String, List<String>> idsBySemanticKey = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (!"conditionalRequired".equals(rule.type())) {
                continue;
            }
            String key = rule.semanticKey();
            idsBySemanticKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule.id());
        }

        Map<String, List<String>> duplicates = idsBySemanticKey.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));

        assertThat(duplicates)
                .as("Page-level conditionalRequired rules must not have duplicate semantics by type + code + then.targets")
                .isEmpty();
    }

    @Test
    void knownOverseasRequiredRulePairsAreNotBothPresent() throws Exception {
        String yaml = Files.readString(YAML);
        List<String> ruleIds = extractPageLevelValidationRules(yaml).stream()
                .map(Rule::id)
                .toList();

        assertThat(ruleIds)
                .as("Keep only one overseas phone-number-required rule")
                .doesNotContainSubsequence(
                        "overseasPhone.requiredWhenCodePresent",
                        "overseasPhoneNumber.requiredWhenCodePresent");

        assertThat(ruleIds)
                .as("Keep only one overseas country-code-required rule")
                .doesNotContainSubsequence(
                        "overseasCountryCode.requiredWhenPhonePresent",
                        "overseasCountryCode.requiredWhenOverseasPhonePresent");
    }

    private List<Rule> extractPageLevelValidationRules(String yaml) {
        Matcher validationsMatcher = Pattern.compile("(?m)^      validations:\\s*$").matcher(yaml);
        int start = -1;
        while (validationsMatcher.find()) {
            start = validationsMatcher.end();
        }
        assertThat(start)
                .as("Expected page-level validations section in personal-information YAML")
                .isGreaterThanOrEqualTo(0);

        String rulesSection = yaml.substring(start);
        Matcher blockMatcher = Pattern.compile(
                "(?ms)^\\s{6,8}- id:\\s*([^\\n]+)\\n(.*?)(?=^\\s{6,8}- id:|\\z)")
                .matcher(rulesSection);

        List<Rule> rules = new ArrayList<>();
        while (blockMatcher.find()) {
            String id = blockMatcher.group(1).trim();
            String block = blockMatcher.group(2);
            rules.add(new Rule(
                    id,
                    firstScalar(block, "type"),
                    firstScalar(block, "code"),
                    extractTargets(block)));
        }
        return rules;
    }

    private String firstScalar(String block, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s+" + Pattern.quote(key) + ":\\s*([^\\n#]+)\\s*$")
                .matcher(block);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private List<String> extractTargets(String block) {
        Matcher matcher = Pattern.compile("(?m)^\\s+targets:\\s*$").matcher(block);
        if (!matcher.find()) {
            return List.of();
        }

        List<String> targets = new ArrayList<>();
        String[] lines = block.substring(matcher.end()).split("\\R");
        for (String line : lines) {
            if (line.matches("^\\s{10,}-\\s+[^\\s].*")) {
                targets.add(line.replaceFirst("^\\s*-\\s+", "").trim());
                continue;
            }
            if (!line.isBlank() && line.matches("^\\s{0,8}[^-].*")) {
                break;
            }
        }
        return targets.stream().sorted().toList();
    }

    private record Rule(String id, String type, String code, List<String> targets) {
        private String semanticKey() {
            return String.join("|",
                    Objects.toString(type, ""),
                    Objects.toString(code, ""),
                    String.join(",", targets));
        }
    }
}
