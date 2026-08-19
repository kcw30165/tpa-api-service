package com.bct.ngtpa.apiservice.application.service.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitBucket;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import org.junit.jupiter.api.Test;

class Sha256DestinationBucketIdGeneratorTest {

  private final DestinationBucketIdGenerator generator = new Sha256DestinationBucketIdGenerator();

  @Test
  void generatesDeterministicEmailBucketId() {
    String first = generator.generateBucketId(DestinationType.EMAIL, "Member@Example.COM");
    String second = generator.generateBucketId(DestinationType.EMAIL, " member@example.com ");

    assertThat(first).isEqualTo(second);
    assertThat(first).startsWith("dest_EMAIL_");
    assertThat(first)
        .doesNotContain("member@example.com")
        .doesNotContain("Member@Example.COM")
        .doesNotContain("@");
  }

  @Test
  void generatesDifferentBucketIdsForDifferentEmails() {
    String first = generator.generateBucketId(DestinationType.EMAIL, "member1@example.com");
    String second = generator.generateBucketId(DestinationType.EMAIL, "member2@example.com");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void generatesDeterministicMobileBucketId() {
    String withSpaces = generator.generateBucketId(DestinationType.MOBILE, " 9123 4567 ");
    String compact = generator.generateBucketId(DestinationType.MOBILE, "91234567");
    String withHyphen = generator.generateBucketId(DestinationType.MOBILE, "9123-4567");

    assertThat(withSpaces).isEqualTo(compact).isEqualTo(withHyphen);
    assertThat(withSpaces).startsWith("dest_MOBILE_");
    assertThat(withSpaces).doesNotContain("91234567");
  }

  @Test
  void preservesLeadingPlusForMobileNormalization() {
    String withFormatting = generator.generateBucketId(DestinationType.MOBILE, "+852 9123-4567");
    String compactWithPlus = generator.generateBucketId(DestinationType.MOBILE, "+85291234567");
    String compactWithoutPlus = generator.generateBucketId(DestinationType.MOBILE, "85291234567");

    assertThat(withFormatting).isEqualTo(compactWithPlus);
    assertThat(withFormatting).isNotEqualTo(compactWithoutPlus);
    assertThat(withFormatting).doesNotContain("+85291234567");
  }

  @Test
  void usesDestinationTypeAsHashDomainSeparator() {
    String emailBucket = generator.generateBucketId(DestinationType.EMAIL, "12345");
    String mobileBucket = generator.generateBucketId(DestinationType.MOBILE, "12345");

    assertThat(emailBucket).isNotEqualTo(mobileBucket);
    assertThat(emailBucket).startsWith("dest_EMAIL_");
    assertThat(mobileBucket).startsWith("dest_MOBILE_");
  }

  @Test
  void rejectsBlankDestinationWithoutLeakingValue() {
    assertBlankDestinationRejected(null);
    assertBlankDestinationRejected("   ");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generateBucketId(DestinationType.MOBILE, " - ( ) "));
    assertThat(exception.getMessage())
        .doesNotContain("- ( )")
        .doesNotContain("91234567")
        .contains("destination");
  }

  @Test
  void rejectsNullDestinationType() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generateBucketId(null, "member@example.com"));

    assertThat(exception.getMessage())
        .contains("destination type")
        .doesNotContain("member@example.com");
  }

  @Test
  void bucketIdContainsOnlyRedisSafeCharacters() {
    String bucketId = generator.generateBucketId(DestinationType.EMAIL, "member@example.com");

    assertThat(bucketId).matches("^[A-Za-z0-9_-]+$");
    assertThat(bucketId)
        .doesNotContain("@")
        .doesNotContain("+")
        .doesNotContain(" ")
        .doesNotContain("member@example.com");
  }

  @Test
  void generatedBucketIdCanBeUsedInRateLimitCommand() {
    String bucketId = generator.generateBucketId(DestinationType.EMAIL, "member@example.com");

    var command =
        new RateLimitCommand(
            RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH, bucketId, true, 3, 600, "request-123");

    assertThat(command.bucket()).isEqualTo(RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH);
    assertThat(command.bucketId()).isEqualTo(bucketId);
    assertThat(command.enabled()).isTrue();
    assertThat(command.maxAttempts()).isEqualTo(3);
    assertThat(command.windowSeconds()).isEqualTo(600);
    assertThat(command.correlationId()).isEqualTo("request-123");
  }

  private void assertBlankDestinationRejected(String destination) {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generateBucketId(DestinationType.EMAIL, destination));
    assertThat(exception.getMessage())
        .doesNotContain("member@example.com")
        .doesNotContain("91234567")
        .contains("destination");
  }
}
