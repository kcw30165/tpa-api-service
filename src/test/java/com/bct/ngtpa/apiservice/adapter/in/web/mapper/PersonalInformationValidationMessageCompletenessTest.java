package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PersonalInformationValidationMessageCompletenessTest {

    private static final Path YAML = Path.of("src/main/resources/application-page-personal-information.yml");

    @Test
    void everyValidationMessageCodeHasBracketedDisplayEntry() throws Exception {
        String yaml = Files.readString(YAML);
        Set<String> messageCodes = extractMessageCodes(yaml);

        List<String> missingDisplayEntries = new ArrayList<>();
        for (String messageCode : messageCodes) {
            String bracketedDisplayKey = "[" + messageCode + "]";
            if (!yaml.contains(bracketedDisplayKey)) {
                missingDisplayEntries.add(messageCode);
            }
        }

        assertThat(missingDisplayEntries)
                .as("Every validation messageCode must resolve through the bracketed display map used by GET response normalization")
                .isEmpty();
    }

    @Test
    void knownRuntimeValidationCodesHaveResolvedDisplayMessages() throws Exception {
        String yaml = Files.readString(YAML);

        assertThat(yaml)
                .as("overseasPhoneExtension.maxLength currently returns code without message")
                .contains("[personalInformation.overseasPhoneExtension.invalid.message]");
        assertThat(yaml)
                .as("email.minLength currently returns code without message")
                .contains("[personalInformation.email.tooShort.message]");
        assertThat(yaml)
                .as("mailingCountry.requiredWhenMailingAddressCaptured currently returns code without message")
                .contains("[personalInformation.mailingCountry.required.message]");
    }

    @Test
    void knownRuntimeValidationDefinitionsCarryMessageCodes() throws Exception {
        String yaml = Files.readString(YAML);

        assertRuleBlockContains(
                yaml,
                "id: email.minLength",
                "messageCode: personalInformation.email.tooShort.message");
        assertRuleBlockContains(
                yaml,
                "id: mailingCountry.requiredWhenMailingAddressCaptured",
                "messageCode: personalInformation.mailingCountry.required.message");

        assertThat(yaml)
                .as("overseasPhoneExtension should define an explicit maxLength validation with a messageCode instead of relying on generated metadata only")
                .contains("id: overseasPhoneExtension.maxLength")
                .contains("messageCode: personalInformation.overseasPhoneExtension.invalid.message");
    }

    private Set<String> extractMessageCodes(String yaml) {
        Set<String> messageCodes = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*messageCode:\\s*([^\\s#]+)\\s*$").matcher(yaml);
        while (matcher.find()) {
            messageCodes.add(matcher.group(1).trim());
        }
        return messageCodes;
    }

    private void assertRuleBlockContains(String yaml, String ruleIdLine, String expectedLine) {
        int ruleStart = yaml.indexOf(ruleIdLine);
        assertThat(ruleStart)
                .as("Expected YAML rule to exist: %s", ruleIdLine)
                .isGreaterThanOrEqualTo(0);

        int nextRuleStart = yaml.indexOf("\n            - id: ", ruleStart + ruleIdLine.length());
        int pageRuleStart = yaml.indexOf("\n      - id: ", ruleStart + ruleIdLine.length());
        int blockEnd = yaml.length();
        if (nextRuleStart > ruleStart) {
            blockEnd = Math.min(blockEnd, nextRuleStart);
        }
        if (pageRuleStart > ruleStart) {
            blockEnd = Math.min(blockEnd, pageRuleStart);
        }

        String block = yaml.substring(ruleStart, blockEnd);
        assertThat(block)
                .as("Expected YAML rule block %s to contain %s", ruleIdLine, expectedLine)
                .contains(expectedLine);
    }
}
