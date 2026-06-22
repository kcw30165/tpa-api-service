package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CopilotGuidanceContractTest {

    private static final Path GITHUB_DIR = Path.of(".github");
    private static final Path COPILOT_INSTRUCTIONS = GITHUB_DIR.resolve("copilot-instructions.md");
    private static final Path INSTRUCTIONS_DIR = GITHUB_DIR.resolve("instructions");
    private static final Path CURRENT_PROJECT_DIRECTION =
            INSTRUCTIONS_DIR.resolve("current-project-direction.instructions.md");
    private static final Path SKILLS_DIR = GITHUB_DIR.resolve("skills");
    private static final Path PROMPTS_DIR = GITHUB_DIR.resolve("prompts");
    private static final Path ARCHIVE_DIR = Path.of("docs/development-log/copilot-prompts");
    private static final Path ARCHIVE_README = ARCHIVE_DIR.resolve("README.md");

    @Test
    void requiredActiveGuidanceFilesExist() throws Exception {
        assertTrue(Files.isRegularFile(COPILOT_INSTRUCTIONS),
                ".github/copilot-instructions.md must exist");
        assertTrue(Files.isRegularFile(CURRENT_PROJECT_DIRECTION),
                ".github/instructions/current-project-direction.instructions.md must exist");
        assertFalse(filesWithExtension(INSTRUCTIONS_DIR, ".instructions.md").isEmpty(),
                ".github/instructions must contain active path-scoped instruction files");
        assertFalse(skillFiles().isEmpty(),
                ".github/skills must contain active reusable workflow skills");
    }

    @Test
    void archivedPromptReadmeExists() {
        assertTrue(Files.isRegularFile(ARCHIVE_README),
                "docs/development-log/copilot-prompts/README.md must exist");
    }

    @Test
    void githubPromptsContainsNoMarkdownPromptFiles() throws Exception {
        if (!Files.exists(PROMPTS_DIR)) {
            return;
        }

        List<Path> markdownFiles = filesWithExtension(PROMPTS_DIR, ".md");
        assertTrue(markdownFiles.isEmpty(),
                ".github/prompts must be absent or contain no markdown prompt files, but found: " + markdownFiles);
    }

    @Test
    void activeGuidanceReferencesCanonicalCurrentDirection() throws Exception {
        String copilotInstructions = Files.readString(COPILOT_INSTRUCTIONS);
        String currentDirection = Files.readString(CURRENT_PROJECT_DIRECTION);

        assertTrue(copilotInstructions.contains(".github/instructions/current-project-direction.instructions.md"),
                "Active Copilot guidance must point to the canonical current project direction file");
        assertTrue(currentDirection.toLowerCase(Locale.ROOT).contains("canonical current-direction instruction")
                        || currentDirection.toLowerCase(Locale.ROOT).contains("highest-priority implementation direction"),
                "Current project direction file must remain explicitly canonical");
    }

    @Test
    void activeGuidanceEmphasizesCurrentDirectionThemes() throws Exception {
        String activeGuidance = activeGuidanceContent();

        assertTrue(activeGuidance.contains("PortalAccessContext"),
                "Active guidance must emphasize PortalAccessContext");
        assertTrue(activeGuidance.contains("accountEnv"),
                "Active guidance must emphasize accountEnv");
        assertTrue(activeGuidance.contains("Accept-Language"),
                "Active guidance must emphasize Accept-Language");
        assertTrue(activeGuidance.contains("X-Request-Id"),
                "Active guidance must emphasize X-Request-Id");
        assertTrue(activeGuidance.contains("MutationResponse"),
                "Active guidance must emphasize MutationResponse");
        assertTrue(activeGuidance.contains("adapter/out/apim") && activeGuidance.contains("must not import APIM DTOs"),
                "Active guidance must emphasize APIM boundary isolation");
        assertTrue(activeGuidance.toLowerCase(Locale.ROOT).contains("fail-first tdd"),
                "Active guidance must emphasize fail-first TDD");
    }

    @Test
    void legacyTermsAppearOnlyInExplicitGuardrailContexts() throws Exception {
        List<LegacyGuard> legacyTerms = List.of(
                new LegacyGuard("MemberContext", Pattern.compile("\\bMemberContext\\b")),
                new LegacyGuard("TemporaryMemberContext", Pattern.compile("\\bTemporaryMemberContext\\b")),
                new LegacyGuard("MemberOwnerContext", Pattern.compile("\\bMemberOwnerContext\\b")),
                new LegacyGuard("deploymentEnv", Pattern.compile("\\bdeploymentEnv\\b")),
                new LegacyGuard("lang query", Pattern.compile("(?i)\\blang\\b")),
                new LegacyGuard("session-id", Pattern.compile("\\bsession-id\\b")),
                new LegacyGuard("actorType", Pattern.compile("\\bactorType\\b")));

        for (Path file : activeGuidanceFiles()) {
            String content = Files.readString(file);
            for (LegacyGuard legacyTerm : legacyTerms) {
                assertLegacyTermOnlyUsedAsGuardrail(file, content, legacyTerm);
            }
        }
    }

    private static void assertLegacyTermOnlyUsedAsGuardrail(Path file, String content, LegacyGuard legacyTerm) {
        String lowerContent = content.toLowerCase(Locale.ROOT);
        Matcher matcher = legacyTerm.pattern().matcher(content);

        while (matcher.find()) {
            int index = matcher.start();
            int start = Math.max(0, index - 180);
            int end = Math.min(lowerContent.length(), matcher.end() + 180);
            String context = lowerContent.substring(start, end);

            boolean guardrailContext = context.contains("do not")
                    || context.contains("must not")
                    || context.contains("should not")
                    || context.contains("not use")
                    || context.contains("not active")
                    || context.contains("fallback")
                    || context.contains("negative")
                    || context.contains("legacy")
                    || context.contains("historical")
                    || context.contains("old")
                    || context.contains("removed")
                    || context.contains("has no")
                    || context.contains("means runtime")
                    || context.contains("compatibility")
                    || context.contains("wins")
                    || context.contains("conflict");

            assertTrue(guardrailContext,
                    () -> file + " uses legacy term '" + legacyTerm.label() + "' outside an explicit guardrail context");
        }
    }

    private static String activeGuidanceContent() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (Path file : activeGuidanceFiles()) {
            builder.append(Files.readString(file)).append('\n');
        }
        return builder.toString();
    }

    private static List<Path> activeGuidanceFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        files.add(COPILOT_INSTRUCTIONS);
        files.addAll(filesWithExtension(INSTRUCTIONS_DIR, ".instructions.md"));
        files.addAll(skillFiles());
        return files;
    }

    private static List<Path> skillFiles() throws IOException {
        if (!Files.exists(SKILLS_DIR)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(SKILLS_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> filesWithExtension(Path root, String suffix) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private record LegacyGuard(String label, Pattern pattern) {
    }
}