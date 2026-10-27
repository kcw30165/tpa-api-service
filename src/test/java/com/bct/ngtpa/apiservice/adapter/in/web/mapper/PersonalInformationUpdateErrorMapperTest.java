package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class PersonalInformationUpdateErrorMapperTest {
  @Test
  void retiredLegacyMessageGeneratingEntryPointHasNoPublicMethod() {
    assertThat(
            Arrays.stream(PersonalInformationUpdateErrorMapper.class.getMethods())
                .map(java.lang.reflect.Method::getName))
        .isNotEmpty()
        .doesNotContain("toApiErrors");
  }

  @Test
  void mapsRequiredUnchangedAndResolvesInvalidFormatFromRealYaml() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              var errors =
                  mapper.toMappedErrors(
                      row(
                          List.of(
                              new UpdatePersonalInformationError(
                                  "FIELD", List.of("email"), "REQUIRED"),
                              new UpdatePersonalInformationError(
                                  "FIELD", List.of("email"), "INVALID_FORMAT"))));
              assertThat(errors)
                  .extracting(com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError::code)
                  .containsExactly(
                      "personalInformation.field.required", "personalInformation.email.invalid");
              assertThat(errors)
                  .allSatisfy(error -> assertThat(error.targets()).containsExactly("emailAddress"));
            });
  }

  @Test
  void mapsCrossFieldToOneErrorWithMultipleYamlTargets() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              assertThat(
                      mapper.toMappedErrors(
                          row(
                              List.of(
                                  new UpdatePersonalInformationError(
                                      "CROSS_FIELD",
                                      List.of("addr1|addr2"),
                                      "AT_LEAST_ONE_REQUIRED")))))
                  .containsExactly(
                      new com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError(
                          "CROSS_FIELD",
                          "personalInformation.address.atLeastOneRequired",
                          List.of("residentialAddressLine1", "residentialAddressLine2")));
            });
  }

  @ParameterizedTest
  @MethodSource("tasCodes")
  void mapsTasCodesForSelectedAndOtherAccounts(
      boolean selected,
      String downstreamCode,
      String javaCode,
      String downstreamFields,
      String target) {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              var account =
                  new UpdatePersonalInformationResult(
                      selected,
                      false,
                      "118",
                      "2",
                      "JP",
                      null,
                      null,
                      null,
                      List.of(
                          new UpdatePersonalInformationError(
                              "CROSS_FIELD", List.of(downstreamFields), downstreamCode)));
              assertThat(mapper.toMappedErrors(account))
                  .containsExactly(new MappedError("CROSS_FIELD", javaCode, List.of(target)));
            });
  }

  static Stream<Arguments> tasCodes() {
    return Stream.of(true, false)
        .flatMap(
            selected ->
                Stream.of(
                    Arguments.of(
                        selected,
                        "MISSING_OVERSEAS_COUNTRY",
                        "personalInformation.overseasCountryCode.requiredWhenPhonePresent",
                        "other-phone-country|other-phone",
                        "overseasCountryCode"),
                    Arguments.of(
                        selected,
                        "MISSING_OVERSEAS_PHONE",
                        "personalInformation.overseasPhoneNumber.requiredWhenCodePresent",
                        "other-phone-country|other-phone",
                        "overseasPhoneNumber"),
                    Arguments.of(
                        selected,
                        "MISSING_BUSSINESS_PHONE",
                        "personalInformation.businessPhone.requiredWhenExtensionPresent",
                        "business-phone-ext|business-phone",
                        "hongKongBusinessPhone"),
                    Arguments.of(
                        selected,
                        "INVALID_EMAIL",
                        "personalInformation.email.invalid",
                        "email",
                        "emailAddress"),
                    Arguments.of(
                        selected,
                        "INVALID_FAX",
                        "personalInformation.faxNo.invalid",
                        "fax",
                        "faxNo"),
                    Arguments.of(
                        selected,
                        "INVALID_MOBILE_NUMBER",
                        "personalInformation.mobilePhone.invalid",
                        "mobile-number",
                        "hongKongMobilePhone"),
                    Arguments.of(
                        selected,
                        "INVALID_HOME_PHONE",
                        "personalInformation.homeTel.invalid",
                        "home-phone",
                        "homeTel"),
                    Arguments.of(
                        selected,
                        "INVALID_ADDRESS",
                        "personalInformation.residentialAddress.invalid",
                        "addr1",
                        "residentialAddressLine1"),
                    Arguments.of(
                        selected,
                        "INVALID_CORR_ADDRESS",
                        "personalInformation.mailingAddress.invalid",
                        "corr-addr1",
                        "mailingAddressLine1"),
                    Arguments.of(
                        selected,
                        "BLOCKED_PO_ADDRESS",
                        "personalInformation.address.poBoxBlocked",
                        "addr1",
                        "residentialAddressLine1")));
  }

  @Test
  void missingCodeTargetsFollowLoadedYamlRatherThanTasFields() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var properties = context.getBean(BffPagesProperties.class);
              properties.getPages().get("personalInformation").getValidations().stream()
                  .filter(
                      rule ->
                          "personalInformation.overseasCountryCode.requiredWhenPhonePresent"
                              .equals(rule.getCode()))
                  .findFirst()
                  .orElseThrow()
                  .getThen()
                  .setTargets(List.of("emailAddress"));
              var mapper = new PersonalInformationUpdateErrorMapper(properties);
              assertThat(
                      mapper.toMappedErrors(
                          row(
                              List.of(
                                  new UpdatePersonalInformationError(
                                      "CROSS_FIELD",
                                      List.of("unknown-tas-field"),
                                      "MISSING_OVERSEAS_COUNTRY")))))
                  .containsExactly(
                      new MappedError(
                          "CROSS_FIELD",
                          "personalInformation.overseasCountryCode.requiredWhenPhonePresent",
                          List.of("emailAddress")));
            });
  }

  @Test
  void unknownCodeLogsOnlyTheCodeAtWarn() {
    assertUnknownCodeLog("NEW_TAS.CODE-1", "NEW_TAS.CODE-1");
    assertUnknownCodeLog("NEW\r\n\t[CODE]{}=!", "NEWCODE");
    assertUnknownCodeLog("A".repeat(80), "A".repeat(64));
    assertUnknownCodeLog(null, "");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "page",
        "validations",
        "rule",
        "type",
        "then",
        "nullTargets",
        "emptyTargets",
        "unknownTarget"
      })
  void missingCodeWithInvalidYamlFailsExplicitly(String missing) {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var properties = context.getBean(BffPagesProperties.class);
              var page = properties.getPages().get("personalInformation");
              var rule =
                  page.getValidations().stream()
                      .filter(
                          value ->
                              "personalInformation.overseasCountryCode.requiredWhenPhonePresent"
                                  .equals(value.getCode()))
                      .findFirst()
                      .orElseThrow();
              switch (missing) {
                case "page" -> properties.getPages().clear();
                case "validations" -> page.setValidations(null);
                case "rule" -> page.setValidations(List.of());
                case "type" -> rule.setType("pattern");
                case "then" -> rule.setThen(null);
                case "nullTargets" -> rule.getThen().setTargets(null);
                case "emptyTargets" -> rule.getThen().setTargets(List.of());
                case "unknownTarget" -> rule.getThen().setTargets(List.of("not-a-frontend-field"));
                default -> throw new AssertionError(missing);
              }
              var mapper = new PersonalInformationUpdateErrorMapper(properties);
              var account =
                  row(
                      List.of(
                          new UpdatePersonalInformationError(
                              "CROSS_FIELD", List.of("other-phone"), "MISSING_OVERSEAS_COUNTRY")));
              assertThatThrownBy(() -> mapper.toMappedErrors(account))
                  .isInstanceOf(IllegalStateException.class);
            });
  }

  private void assertUnknownCodeLog(String rawCode, String safeCode) {
    var logger = (Logger) LoggerFactory.getLogger(PersonalInformationUpdateErrorMapper.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      PersonalInformationYamlContextRunner.create()
          .run(
              context -> {
                var mapper =
                    new PersonalInformationUpdateErrorMapper(
                        context.getBean(BffPagesProperties.class));
                assertThat(
                        mapper.toMappedErrors(
                            row(
                                List.of(
                                    new UpdatePersonalInformationError(
                                        "FIELD", List.of("email"), rawCode)))))
                    .containsExactly(
                        new MappedError(
                            "FIELD",
                            "personalInformation.update.rejected",
                            List.of("emailAddress")));
              });
      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                    .isEqualTo(
                        "event=personal-information-update-unknown-downstream-code code="
                            + safeCode);
                assertThat(event.getArgumentArray()).containsExactly(safeCode);
                assertThat(event.getThrowableProxy()).isNull();
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static UpdatePersonalInformationResult row(List<UpdatePersonalInformationError> errors) {
    return new UpdatePersonalInformationResult(
        true, false, "118", "2", "DB", null, null, null, errors);
  }
}
