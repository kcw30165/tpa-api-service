package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonalInformationMappedUpdateErrorsTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest
  @MethodSource("codes")
  void mapsApprovedCodesForSelectedAndOtherAccounts(
      boolean selected, String downstream, String expected) throws Exception {
    var mapped = map(mapper(), account(selected, false, "FIELD", downstream, List.of("email")));
    assertThat(mapped.size()).isEqualTo(1);
    assertThat(mapped.get(0).path("code").asText()).isEqualTo(expected);
    assertThat(mapped.get(0).path("type").asText()).isEqualTo("FIELD");
    assertThat(mapped.get(0).path("targets").get(0).asText()).isEqualTo("emailAddress");
    assertThat(mapped.get(0).size()).isEqualTo(3);
    assertThat(mapped.get(0).has("message")).isFalse();
    assertThat(mapped.get(0).has("fields")).isFalse();
  }

  static Stream<Arguments> codes() {
    return Stream.of(true, false)
        .flatMap(
            selected ->
                Stream.of(
                    Arguments.of(selected, "REQUIRED", "personalInformation.field.required"),
                    Arguments.of(
                        selected, "INVALID_FORMAT", "personalInformation.field.invalidFormat"),
                    Arguments.of(
                        selected,
                        "AT_LEAST_ONE_REQUIRED",
                        "personalInformation.address.atLeastOneRequired"),
                    Arguments.of(selected, "READ_ONLY", "personalInformation.update.rejected"),
                    Arguments.of(
                        selected,
                        "personalInformation.unapproved",
                        "personalInformation.update.rejected"),
                    Arguments.of(selected, "", "personalInformation.update.rejected"),
                    Arguments.of(selected, null, "personalInformation.update.rejected")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void crossFieldIsOneErrorWithOnlyKnownDistinctFrontendTargets(boolean selected) throws Exception {
    var mapped =
        map(
            mapper(),
            account(
                selected,
                false,
                "CROSS_FIELD",
                "AT_LEAST_ONE_REQUIRED",
                List.of("addr1 | missing | addr2 |addr1", "", "  ")));
    assertThat(mapped.size()).isEqualTo(1);
    assertThat(mapped.get(0).path("type").asText()).isEqualTo("CROSS_FIELD");
    assertThat(mapped.get(0).path("targets"))
        .isEqualTo(JSON.valueToTree(List.of("firstLine", "secondLine")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void unknownFieldsAreOmittedWithoutDroppingTheError(boolean selected) throws Exception {
    var mapped = map(mapper(), account(selected, false, "FIELD", "REQUIRED", List.of("unknown")));
    assertThat(mapped.get(0).path("targets").isEmpty()).isTrue();
    assertThat(mapped.get(0).path("code").asText()).isEqualTo("personalInformation.field.required");
  }

  @ParameterizedTest
  @MethodSource("types")
  void typeIsPassedThroughWithoutInventingOrNormalizingIt(String type) throws Exception {
    var mapped = map(mapper(), account(true, false, type, "REQUIRED", List.of()));
    assertThat(mapped.get(0).get("type")).isEqualTo(JSON.valueToTree(type));
  }

  static Stream<Arguments> types() {
    return Stream.of(
        Arguments.of("FIELD"),
        Arguments.of("CROSS_FIELD"),
        Arguments.of((String) null),
        Arguments.of(""),
        Arguments.of("custom"));
  }

  @Test
  void failedOtherAccountWithNoErrorsGetsOneNullTypedCodeOnlyFallback() throws Exception {
    var mapped =
        map(
            mapper(),
            new UpdatePersonalInformationResult(
                false, false, "118", "3", "DB", null, null, null, List.of()));
    assertThat(mapped.size()).isEqualTo(1);
    assertThat(mapped.get(0).get("type").isNull()).isTrue();
    assertThat(mapped.get(0).path("code").asText())
        .isEqualTo("personalInformation.update.rejected");
    assertThat(mapped.get(0).path("targets").isEmpty()).isTrue();
    assertThat(mapped.get(0).size()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void successfulAccountsProduceNoMappedFailures(boolean selected) throws Exception {
    assertThat(
            map(mapper(), account(selected, true, "FIELD", "REQUIRED", List.of("email"))).isEmpty())
        .isTrue();
  }

  @Test
  void missingSelectedErrorsRemainAnInvariantViolationNotAnOtherAccountFallback() {
    assertThatThrownBy(
            () ->
                map(
                    mapper(),
                    new UpdatePersonalInformationResult(
                        true, false, "118", "3", "DB", null, null, null, List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Selected account update errors are missing.");
  }

  @Test
  void realYamlProvidesTheReverseErrorBindings() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              var mapped =
                  map(
                      mapper,
                      account(
                          false,
                          false,
                          "CROSS_FIELD",
                          "REQUIRED",
                          List.of("email|addr1|corr-addr1")));
              assertThat(mapped.get(0).path("targets"))
                  .isEqualTo(
                      JSON.valueToTree(
                          List.of(
                              "emailAddress", "residentialAddressLine1", "mailingAddressLine1")));
            });
  }

  @Test
  void realYamlResolvesInvalidFormatForSelectedAndOtherAccounts() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              for (boolean selected : List.of(true, false)) {
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("email"),
                    "personalInformation.email.invalid",
                    List.of("emailAddress"));
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("addr1"),
                    "personalInformation.residentialAddress.invalid",
                    List.of("residentialAddressLine1"));
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("addr1|addr2|addr3"),
                    "personalInformation.residentialAddress.invalid",
                    List.of(
                        "residentialAddressLine1",
                        "residentialAddressLine2",
                        "residentialAddressLine3"));
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("unknown"),
                    "personalInformation.field.invalidFormat",
                    List.of());
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of(),
                    "personalInformation.field.invalidFormat",
                    List.of());
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("country"),
                    "personalInformation.field.invalidFormat",
                    List.of("residentialCountry"));
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("email|addr1"),
                    "personalInformation.field.invalidFormat",
                    List.of("emailAddress", "residentialAddressLine1"));
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("email|country"),
                    "personalInformation.field.invalidFormat",
                    List.of("emailAddress", "residentialCountry"));
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("unknown|email|email"),
                    "personalInformation.email.invalid",
                    List.of("emailAddress"));
              }
            });
  }

  @Test
  void multipleDistinctInvalidCodesOnOneTargetFallBackAndNonInvalidRulesAreIgnored() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var properties = context.getBean(BffPagesProperties.class);
              var email =
                  properties.getPages().get("personalInformation").getForm().getSections().stream()
                      .flatMap(section -> section.getFields().stream())
                      .filter(field -> "emailAddress".equals(field.getId()))
                      .findFirst()
                      .orElseThrow();
              var mapper = new PersonalInformationUpdateErrorMapper(properties);
              var rules = new ArrayList<>(email.getValidations());
              rules.add(rule("personalInformation.address.poBoxBlocked"));
              rules.add(rule("personalInformation.email.requiredWhenPresent"));
              rules.add(rule(null));
              rules.add(rule(""));
              email.setValidations(rules);
              for (boolean selected : List.of(true, false)) {
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("email"),
                    "personalInformation.email.invalid",
                    List.of("emailAddress"));
              }
              rules.add(rule("personalInformation.alternate.invalid"));
              email.setValidations(rules);
              for (boolean selected : List.of(true, false)) {
                assertInvalidFormat(
                    mapper,
                    selected,
                    List.of("email"),
                    "personalInformation.field.invalidFormat",
                    List.of("emailAddress"));
              }
            });
  }

  @Test
  void missingAndPartialConfigurationOmitsTargetsWithoutDroppingErrors() {
    var properties = new BffPagesProperties();
    var mapper = new PersonalInformationUpdateErrorMapper(properties);
    assertInvalidFormat(
        mapper, true, List.of("email"), "personalInformation.field.invalidFormat", List.of());
    properties.setPages(null);
    assertInvalidFormat(
        mapper, true, List.of("email"), "personalInformation.field.invalidFormat", List.of());
    var page = new PageSchemaProperties();
    properties.setPages(Map.of("personalInformation", page));
    assertInvalidFormat(
        mapper, true, List.of("email"), "personalInformation.field.invalidFormat", List.of());
    var form = new FormMetadataProperties();
    page.setForm(form);
    form.setSections(null);
    assertInvalidFormat(
        mapper, true, List.of("email"), "personalInformation.field.invalidFormat", List.of());
    var section = new SectionProperties();
    var noBinding = new FieldProperties();
    noBinding.setId("noBinding");
    var noId = new FieldProperties();
    var blankId = new FieldProperties();
    blankId.setId(" ");
    var email = field("emailAddress", "email", " ");
    email.setValidations(null);
    section.setFields(java.util.Arrays.asList(null, noId, blankId, noBinding, email));
    var empty = new SectionProperties();
    form.setSections(java.util.Arrays.asList(null, empty, section));
    assertInvalidFormat(
        mapper,
        true,
        List.of("email"),
        "personalInformation.field.invalidFormat",
        List.of("emailAddress"));
  }

  private static ValidationRuleProperties rule(String code) {
    var rule = new ValidationRuleProperties();
    rule.setCode(code);
    return rule;
  }

  private static void assertInvalidFormat(
      PersonalInformationUpdateErrorMapper mapper,
      boolean selected,
      List<String> fields,
      String expectedCode,
      List<String> expectedTargets) {
    var errors =
        mapper.toMappedErrors(account(selected, false, "CROSS_FIELD", "INVALID_FORMAT", fields));
    assertThat(errors)
        .containsExactly(
            new com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError(
                "CROSS_FIELD", expectedCode, expectedTargets));
  }

  @Test
  void unknownAndFallbackDiagnosticsDoNotContainAccountIdentifiersOrValues() throws Exception {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(PersonalInformationUpdateErrorMapper.class);
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      map(mapper(), account(false, false, "FIELD", "REQUIRED", List.of("unknown")));
      map(
          mapper(),
          new UpdatePersonalInformationResult(
              false,
              false,
              "secret-policy",
              "secret-cert",
              "DB",
              "secret-ref",
              null,
              null,
              List.of()));
      var messages =
          appender.list.stream()
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
              .toList();
      assertThat(messages)
          .anyMatch(message -> message.contains("unknown-field") && message.contains("unknown"));
      assertThat(messages).anyMatch(message -> message.contains("other-account-errors-missing"));
      assertThat(String.join("\n", messages))
          .doesNotContain("secret-policy", "secret-cert", "secret-ref");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void mappedErrorsAndTheirTargetsAreImmutable() {
    var targets = new ArrayList<>(List.of("emailAddress"));
    var error =
        new com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError("FIELD", "code", targets);
    targets.clear();
    assertThat(error.targets()).containsExactly("emailAddress");
    assertThatThrownBy(() -> error.targets().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    var errors =
        mapper().toMappedErrors(account(true, false, "FIELD", "REQUIRED", List.of("email")));
    assertThatThrownBy(() -> errors.clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  private static JsonNode map(
      PersonalInformationUpdateErrorMapper mapper, UpdatePersonalInformationResult account) {
    return JSON.valueToTree(mapper.toMappedErrors(account));
  }

  private static UpdatePersonalInformationResult account(
      boolean selected, boolean success, String type, String code, List<String> fields) {
    return new UpdatePersonalInformationResult(
        selected,
        success,
        "118",
        "3",
        "DB",
        null,
        null,
        null,
        List.of(new UpdatePersonalInformationError(type, fields, code)));
  }

  private static PersonalInformationUpdateErrorMapper mapper() {
    var email = field("emailAddress", "email", "email-config");
    var first = field("firstLine", "addr1", null);
    var second = field("secondLine", null, "addr2");
    var section = new SectionProperties();
    section.setFields(List.of(email, first, second));
    var form = new FormMetadataProperties();
    form.setSections(List.of(section));
    var page = new PageSchemaProperties();
    page.setForm(form);
    var properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return new PersonalInformationUpdateErrorMapper(properties);
  }

  private static FieldProperties field(String id, String data, String config) {
    var field = new FieldProperties();
    field.setId(id);
    var binding = new ApimBindingProperties();
    binding.setData(data);
    binding.setConfig(config);
    field.setApimBinding(binding);
    return field;
  }
}
