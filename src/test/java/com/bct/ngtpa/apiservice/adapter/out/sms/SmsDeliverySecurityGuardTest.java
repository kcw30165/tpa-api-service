package com.bct.ngtpa.apiservice.adapter.out.sms;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.sms.config.SmsProperties;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SmsDeliverySecurityGuardTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SmsSenderAdapter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void smsAdapterDoesNotLogPasswordFullPhoneOrContent() {
        SmsSenderAdapter adapter = adapterWith(baseProperties(), request -> Mono.error(new IllegalStateException(
                "provider leaked sms-password 91234567 Your code is 123456")));

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Your code is 123456", "HK", "req-123")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SenderStatus.UNKNOWN_OUTCOME);
                    assertThat(result.failureCode()).isEqualTo("SMS_REQUEST_OUTCOME_UNKNOWN");
                    assertThat(result.failureMessage()).doesNotContain(
                            "sms-password", "91234567", "123456", "Your code is 123456");
                })
                .verifyComplete();

        String logOutput = logOutput();
        assertThat(logOutput).doesNotContain("sms-password", "91234567", "123456", "Your code is 123456");
        assertThat(logOutput).contains("contentLogged=false");
    }

    private SmsSenderAdapter adapterWith(SmsProperties properties, ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .exchangeFunction(exchangeFunction)
                .build();
        return new SmsSenderAdapter(webClient, properties);
    }

    private SmsProperties baseProperties() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://sms.example.test");
        properties.setSendSinglePath("/sms/ext/sendSingleSMS.do");
        properties.setUsername("sms-user");
        properties.setPassword("sms-password");
        properties.setSendId("BCT");
        properties.setClassCode("OTP");
        properties.setDefaultCountry("HK");
        properties.setTimeoutMillis(5000);
        return properties;
    }

    private String logOutput() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
