package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Binds the REAL application-page-personal-information.yml and checks D-31: a conditionalRequired
 * rule fails only when its trigger is submitted non-blank and its target is submitted blank. A
 * target key that is absent from the request is skipped (the stored value is assumed unchanged and
 * the downstream system decides).
 */
class PersonalInformationConditionalRequiredRealYamlTest {

  private static final String EMAIL = "member@example.test";
  private static final String BUSINESS_PHONE_CODE =
      "personalInformation.businessPhone.requiredWhenExtensionPresent";
  private static final String OVERSEAS_PHONE_CODE =
      "personalInformation.overseasPhoneNumber.requiredWhenCodePresent";
  private static final String OVERSEAS_COUNTRY_CODE =
      "personalInformation.overseasCountryCode.requiredWhenPhonePresent";

  @Test
  void overseasPhoneWithoutCountryCodeKeyIsSkipped() {
    validateWithRealYaml(
        Map.of("overseasPhoneNumber", "12345678", "emailAddress", EMAIL),
        errors ->
            assertThat(errors).noneMatch(error -> OVERSEAS_COUNTRY_CODE.equals(error.code())));
  }

  @Test
  void overseasPhoneWithBlankCountryCodeIsRejected() {
    validateWithRealYaml(
        Map.of("overseasPhoneNumber", "12345678", "overseasCountryCode", "", "emailAddress", EMAIL),
        errors ->
            assertThat(errors)
                .anyMatch(
                    error ->
                        OVERSEAS_COUNTRY_CODE.equals(error.code())
                            && error.targets().equals(List.of("overseasCountryCode"))));
  }

  @Test
  void businessPhoneExtensionWithoutBusinessPhoneKeyIsSkipped() {
    validateWithRealYaml(
        Map.of("hongKongBusinessPhoneExtension", "123", "emailAddress", EMAIL),
        errors -> assertThat(errors).noneMatch(error -> BUSINESS_PHONE_CODE.equals(error.code())));
  }

  @Test
  void businessPhoneExtensionWithBlankBusinessPhoneIsRejected() {
    validateWithRealYaml(
        Map.of(
            "hongKongBusinessPhoneExtension", "123",
            "hongKongBusinessPhone", "",
            "emailAddress", EMAIL),
        errors ->
            assertThat(errors)
                .anyMatch(
                    error ->
                        BUSINESS_PHONE_CODE.equals(error.code())
                            && error.targets().equals(List.of("hongKongBusinessPhone"))));
  }

  @Test
  void overseasCountryCodeWithoutPhoneKeyIsSkipped() {
    validateWithRealYaml(
        Map.of("overseasCountryCode", "852", "emailAddress", EMAIL),
        errors -> assertThat(errors).noneMatch(error -> OVERSEAS_PHONE_CODE.equals(error.code())));
  }

  @Test
  void overseasCountryCodeWithBlankPhoneIsRejected() {
    validateWithRealYaml(
        Map.of("overseasCountryCode", "852", "overseasPhoneNumber", "", "emailAddress", EMAIL),
        errors ->
            assertThat(errors)
                .anyMatch(
                    error ->
                        OVERSEAS_PHONE_CODE.equals(error.code())
                            && error.targets().equals(List.of("overseasPhoneNumber"))));
  }

  private void validateWithRealYaml(
      Map<String, Object> fields, Consumer<List<ApiError>> assertions) {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              BffPagesProperties properties = context.getBean(BffPagesProperties.class);
              PersonalInformationUpdateYamlValidator validator =
                  new PersonalInformationUpdateYamlValidator(
                      properties,
                      new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                      new ConfigVariantCandidateGenerator());
              assertions.accept(
                  validator.validate(
                      new UpdatePersonalInformationRequest("1.0", false, fields),
                      "en",
                      "JP",
                      "JPM",
                      "OE"));
            });
  }
}
