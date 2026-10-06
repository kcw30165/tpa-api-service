package com.bct.ngtpa.apiservice.shared.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link MaskingService}. Each test constructs its own {@link
 * MaskingProperties} so mode/enabled/mask-length/mask-character scenarios are explicit and
 * independent of the application.yml defaults.
 *
 * <p>Rule under test (per centralized masking policy): email and mobile masking always reveal only
 * the first and last character of the relevant segment (email: local-part before {@code @}; mobile:
 * the full number). The number of mask characters rendered between them is either length-preserving
 * ({@code variable-length}, the default) or a fixed configured count ({@code fixed-length}).
 */
class MaskingServiceTest {

  private static MaskingProperties defaults() {
    return new MaskingProperties();
  }

  private static MaskingService service(MaskingProperties properties) {
    return new MaskingService(properties);
  }

  // ----- maskEmail(): variable-length (default) ------------------------------

  @Test
  void maskEmailVariableLengthKeepsFirstAndLastLocalCharacter() {
    MaskingService service = service(defaults());
    assertThat(service.maskEmail("member@example.com")).isEqualTo("m****r@example.com");
  }

  @Test
  void maskEmailVariableLengthScalesStarCountWithLocalPartLength() {
    MaskingService service = service(defaults());
    assertThat(service.maskEmail("supermember@example.com")).isEqualTo("s*********r@example.com");
  }

  @Test
  void maskEmailVariableLengthSingleCharacterLocalPartMasksEntirely() {
    MaskingService service = service(defaults());
    assertThat(service.maskEmail("a@example.com")).isEqualTo("*@example.com");
  }

  @Test
  void maskEmailVariableLengthTwoCharacterLocalPartMasksEntirely() {
    MaskingService service = service(defaults());
    assertThat(service.maskEmail("ab@example.com")).isEqualTo("**@example.com");
  }

  // ----- maskEmail(): fixed-length --------------------------------------------

  @Test
  void maskEmailFixedLengthUsesConfiguredMaskLengthRegardlessOfInputLength() {
    MaskingProperties properties = defaults();
    properties.getEmail().setMode(MaskMode.FIXED_LENGTH);
    properties.getEmail().setMaskLength(4);
    MaskingService service = service(properties);
    assertThat(service.maskEmail("supermember@example.com")).isEqualTo("s****r@example.com");
    assertThat(service.maskEmail("member@example.com")).isEqualTo("m****r@example.com");
  }

  @Test
  void maskEmailFixedLengthHonorsCustomMaskCharacter() {
    MaskingProperties properties = defaults();
    properties.getEmail().setMode(MaskMode.FIXED_LENGTH);
    properties.getEmail().setMaskLength(3);
    properties.getEmail().setMaskCharacter("#");
    MaskingService service = service(properties);
    assertThat(service.maskEmail("member@example.com")).isEqualTo("m###r@example.com");
  }

  // ----- maskEmail(): guards ----------------------------------------------------

  @Test
  void maskEmailWithoutAtSignThrows() {
    MaskingService service = service(defaults());
    assertThatThrownBy(() -> service.maskEmail("no-at-sign"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void maskEmailBlankInputReturnsUnchanged() {
    MaskingService service = service(defaults());
    assertThat(service.maskEmail("   ")).isEqualTo("   ");
    assertThat(service.maskEmail(null)).isNull();
  }

  @Test
  void maskEmailDisabledReturnsConfiguredPlaceholder() {
    MaskingProperties properties = defaults();
    properties.getEmail().setEnabled(false);
    MaskingService service = service(properties);
    assertThat(service.maskEmail("member@example.com")).isEqualTo("<masked>");
  }

  // ----- maskMobile(): variable-length (default) --------------------------------

  @Test
  void maskMobileVariableLengthKeepsFirstAndLastDigit() {
    MaskingService service = service(defaults());
    assertThat(service.maskMobile("12345678")).isEqualTo("1******8");
  }

  @Test
  void maskMobileVariableLengthShortInputMasksEntirely() {
    MaskingService service = service(defaults());
    assertThat(service.maskMobile("12")).isEqualTo("**");
    assertThat(service.maskMobile("1")).isEqualTo("*");
  }

  // ----- maskMobile(): fixed-length -----------------------------------------------

  @Test
  void maskMobileFixedLengthUsesConfiguredMaskLengthRegardlessOfInputLength() {
    MaskingProperties properties = defaults();
    properties.getMobile().setMode(MaskMode.FIXED_LENGTH);
    properties.getMobile().setMaskLength(4);
    MaskingService service = service(properties);
    assertThat(service.maskMobile("12345678")).isEqualTo("1****8");
    assertThat(service.maskMobile("912345678901")).isEqualTo("9****1");
  }

  @Test
  void maskMobileDisabledReturnsConfiguredPlaceholder() {
    MaskingProperties properties = defaults();
    properties.getMobile().setEnabled(false);
    MaskingService service = service(properties);
    assertThat(service.maskMobile("12345678")).isEqualTo("<masked>");
  }

  @Test
  void maskMobileBlankInputReturnsUnchanged() {
    MaskingService service = service(defaults());
    assertThat(service.maskMobile("")).isEqualTo("");
    assertThat(service.maskMobile(null)).isNull();
  }

  // ----- maskIdentity() -----------------------------------------------------------

  @Test
  void maskIdentityAlwaysReturnsConfiguredPlaceholderRegardlessOfShape() {
    MaskingService service = service(defaults());
    assertThat(service.maskIdentity("G12345678")).isEqualTo("<masked>");
    assertThat(service.maskIdentity("A123B")).isEqualTo("<masked>");
    assertThat(service.maskIdentity("1234567890")).isEqualTo("<masked>");
  }

  @Test
  void maskIdentityDisabledReturnsConfiguredPlaceholder() {
    MaskingProperties properties = defaults();
    properties.getIdentity().setEnabled(false);
    MaskingService service = service(properties);
    assertThat(service.maskIdentity("G12345678")).isEqualTo("<masked>");
  }

  @Test
  void maskIdentityBlankInputReturnsUnchanged() {
    MaskingService service = service(defaults());
    assertThat(service.maskIdentity(null)).isNull();
  }

  // ----- maskDateOfBirth() ---------------------------------------------------------

  @Test
  void maskDateOfBirthAlwaysReturnsConfiguredPlaceholder() {
    MaskingService service = service(defaults());
    assertThat(service.maskDateOfBirth("1990-01-01")).isEqualTo("<redacted>");
  }

  @Test
  void maskDateOfBirthDisabledReturnsGlobalDisabledPlaceholder() {
    MaskingProperties properties = defaults();
    properties.getDateOfBirth().setEnabled(false);
    MaskingService service = service(properties);
    assertThat(service.maskDateOfBirth("1990-01-01")).isEqualTo("<masked>");
  }

  @Test
  void maskDateOfBirthBlankInputReturnsUnchanged() {
    MaskingService service = service(defaults());
    assertThat(service.maskDateOfBirth(null)).isNull();
  }

  // ----- loggingPlaceholder() -----------------------------------------------------------

  @Test
  void loggingPlaceholderReturnsConfiguredValue() {
    MaskingProperties properties = defaults();
    properties.getLogging().setPlaceholder("###");
    MaskingService service = service(properties);
    assertThat(service.loggingPlaceholder()).isEqualTo("###");
  }

  // ----- constructor guard -------------------------------------------------------------

  @Test
  void constructorRejectsNullProperties() {
    assertThatThrownBy(() -> new MaskingService(null)).isInstanceOf(NullPointerException.class);
  }
}
