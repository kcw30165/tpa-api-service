package com.bct.ngtpa.apiservice.adapter.out.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.OtpPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.OtpEmailPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RegistrationMessageDeliveryAdapter}. */
class RegistrationMessageDeliveryAdapterTest {

  private final MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
  private final AuthWorkflowPolicyPort policyPort = mock(AuthWorkflowPolicyPort.class);

  private RegistrationMessageDeliveryAdapter newAdapter() {
    return new RegistrationMessageDeliveryAdapter(delivery, policyPort);
  }

  // ----- constructor guards ----------------------------------------------------

  @Test
  void constructorRejectsNullMessageDeliveryPort() {
    assertThatThrownBy(() -> new RegistrationMessageDeliveryAdapter(null, policyPort))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNullAuthWorkflowPolicyPort() {
    assertThatThrownBy(() -> new RegistrationMessageDeliveryAdapter(delivery, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- policy-not-configured guards ---------------------------------------------

  @Test
  void nullRegistrationPolicyThrows() {
    when(policyPort.registrationPolicy()).thenReturn(null);
    assertThatThrownBy(
            () ->
                newAdapter()
                    .deliverRegistrationOtp(
                        RegistrationContactType.EMAIL, "user@example.com", "123456", 300, "en",
                        "req-1")
                    .block())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void nullOtpPolicyThrows() {
    RegistrationAuthWorkflowPolicy policy = mock(RegistrationAuthWorkflowPolicy.class);
    when(policy.otp()).thenReturn(null);
    when(policyPort.registrationPolicy()).thenReturn(policy);
    assertThatThrownBy(
            () ->
                newAdapter()
                    .deliverRegistrationOtp(
                        RegistrationContactType.EMAIL, "user@example.com", "123456", 300, "en",
                        "req-1")
                    .block())
        .isInstanceOf(IllegalStateException.class);
  }

  // ----- channel routing: MOBILE -> SMS, everything else -> EMAIL ------------------

  private void givenPolicy(OtpPolicy otpPolicy) {
    RegistrationAuthWorkflowPolicy policy = mock(RegistrationAuthWorkflowPolicy.class);
    when(policy.otp()).thenReturn(otpPolicy);
    when(policyPort.registrationPolicy()).thenReturn(policy);
  }

  @Test
  void mobileContactTypeRoutesToSmsChannel() {
    givenPolicy(mock(OtpPolicy.class));
    when(delivery.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    StepVerifier.create(
            newAdapter()
                .deliverRegistrationOtp(
                    RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "req-1"))
        .expectNext(true)
        .verifyComplete();
    verify(delivery)
        .deliver(
            org.mockito.ArgumentMatchers.argThat(
                (MessageDeliveryCommand cmd) -> cmd.channel() == MessageDeliveryChannel.SMS));
  }

  @Test
  void emailContactTypeRoutesToEmailChannel() {
    givenPolicy(mock(OtpPolicy.class));
    when(delivery.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    StepVerifier.create(
            newAdapter()
                .deliverRegistrationOtp(
                    RegistrationContactType.EMAIL, "user@example.com", "123456", 300, "en", "req-1"))
        .expectNext(true)
        .verifyComplete();
    verify(delivery)
        .deliver(
            org.mockito.ArgumentMatchers.argThat(
                (MessageDeliveryCommand cmd) -> cmd.channel() == MessageDeliveryChannel.EMAIL));
  }

  // ----- result-status mapping: ACCEPTED -> true, anything else -> false ---------------

  @Test
  void nonAcceptedStatusYieldsFalse() {
    givenPolicy(mock(OtpPolicy.class));
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.FAILED, null, null, null, null)));
    StepVerifier.create(
            newAdapter()
                .deliverRegistrationOtp(
                    RegistrationContactType.EMAIL, "user@example.com", "123456", 300, "en", "req-1"))
        .expectNext(false)
        .verifyComplete();
  }

  // ----- ttl-to-minutes conversion + minimum-1-minute clamp ---------------------------

  @Test
  void ttlSecondsBelowOneMinuteClampsToOneMinute() {
    givenPolicy(mock(OtpPolicy.class));
    when(delivery.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    StepVerifier.create(
            newAdapter()
                .deliverRegistrationOtp(
                    RegistrationContactType.EMAIL, "user@example.com", "123456", 30, "en", "req-1"))
        .expectNext(true)
        .verifyComplete();
    verify(delivery)
        .deliver(
            org.mockito.ArgumentMatchers.argThat(
                (MessageDeliveryCommand cmd) -> "1".equals(cmd.templateVariables().get("ttlValue"))));
  }

  // ----- email subject/body fallback logic --------------------------------------------

  @Test
  void emailUsesConfiguredSubjectAndBodyWhenPresent() {
    var emailPolicy = mock(OtpEmailPolicy.class);
    when(emailPolicy.subject()).thenReturn("Custom Subject");
    when(emailPolicy.body()).thenReturn("Custom Body {code}");
    OtpPolicy otpPolicy = mock(OtpPolicy.class);
    when(otpPolicy.email()).thenReturn(emailPolicy);
    givenPolicy(otpPolicy);
    when(delivery.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    newAdapter()
        .deliverRegistrationOtp(RegistrationContactType.EMAIL, "user@example.com", "123456", 300, "en", "req-1")
        .block();
    verify(delivery)
        .deliver(
            org.mockito.ArgumentMatchers.argThat(
                (MessageDeliveryCommand cmd) ->
                    "Custom Subject".equals(cmd.subject()) && "Custom Body {code}".equals(cmd.body())));
  }

  @Test
  void emailFallsBackToDefaultSubjectAndBodyWhenBlank() {
    var emailPolicy = mock(OtpEmailPolicy.class);
    when(emailPolicy.subject()).thenReturn("  ");
    when(emailPolicy.body()).thenReturn(null);
    OtpPolicy otpPolicy = mock(OtpPolicy.class);
    when(otpPolicy.email()).thenReturn(emailPolicy);
    when(otpPolicy.smsTemplate()).thenReturn(null);
    givenPolicy(otpPolicy);
    when(delivery.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    newAdapter()
        .deliverRegistrationOtp(RegistrationContactType.EMAIL, "user@example.com", "123456", 300, "en", "req-1")
        .block();
    verify(delivery)
        .deliver(
            org.mockito.ArgumentMatchers.argThat(
                (MessageDeliveryCommand cmd) ->
                    "registration-otp".equals(cmd.subject()) && "{code}".equals(cmd.body())));
  }

  @Test
  void smsUsesSmsTemplateAsBody() {
    OtpPolicy otpPolicy = mock(OtpPolicy.class);
    when(otpPolicy.email()).thenReturn(null);
    when(otpPolicy.smsTemplate()).thenReturn("Your code: {code}");
    givenPolicy(otpPolicy);
    when(delivery.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    newAdapter()
        .deliverRegistrationOtp(RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "req-1")
        .block();
    verify(delivery)
        .deliver(
            org.mockito.ArgumentMatchers.argThat(
                (MessageDeliveryCommand cmd) -> "Your code: {code}".equals(cmd.body())));
  }
}
