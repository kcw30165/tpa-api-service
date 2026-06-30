package com.bct.ngtpa.apiservice.adapter.out.sms;

import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.sms.config.SmsProperties;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SmsSenderAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void mapsSmsMessageToSendSingleSmsPayload() {
        AtomicReference<String> capturedBody = new AtomicReference<>();

        SmsSenderAdapter adapter = adapterWith(baseProperties(), request -> {
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            request.writeTo(mockRequest, ExchangeStrategies.withDefaults()).block();
            capturedBody.set(mockRequest.getBodyAsString().block());
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Hello Member", "HK", "req-123")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
                .verifyComplete();

        JsonNode payload = readJson(capturedBody.get());
        assertThat(payload.get("username").asText()).isEqualTo("sms-user");
        assertThat(payload.get("password").asText()).isEqualTo("sms-password");
        assertThat(payload.get("sendId").asText()).isEqualTo("BCT");
        assertThat(payload.get("classCode").asText()).isEqualTo("OTP");
        assertThat(payload.get("country").asText()).isEqualTo("HK");
        assertThat(payload.get("phone").asText()).isEqualTo("91234567");
        assertThat(payload.get("smsContent").asText()).isEqualTo("Hello Member");
    }

    @Test
    void usesDefaultCountryWhenSmsMessageCountryBlank() {
        AtomicReference<String> capturedBody = new AtomicReference<>();

        SmsSenderAdapter adapter = adapterWith(baseProperties(), request -> {
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            request.writeTo(mockRequest, ExchangeStrategies.withDefaults()).block();
            capturedBody.set(mockRequest.getBodyAsString().block());
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Hello Member", "   ", "req-123")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
                .verifyComplete();

        JsonNode payload = readJson(capturedBody.get());
        assertThat(payload.get("country").asText()).isEqualTo("HK");
    }

    @Test
    void postsToConfiguredSendSingleSmsPath() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();

        SmsSenderAdapter adapter = adapterWith(baseProperties(), request -> {
            capturedUri.set(request.url());
            capturedMethod.set(request.method());
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Hello Member", "HK", "req-123")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
                .verifyComplete();

        assertThat(capturedMethod.get()).isEqualTo(HttpMethod.POST);
        assertThat(capturedUri.get().toString()).isEqualTo("https://sms.example.test/sms/ext/sendSingleSMS.do");
    }

    @Test
    void returnsAcceptedWhenProviderReturns2xx() {
        SmsSenderAdapter adapter = adapterWith(baseProperties(), request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Hello Member", "HK", "req-123")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED);
                    assertThat(result.failureCode()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void returnsFailedOrPropagatesSafeErrorWhenProviderReturns5xx() {
        SmsSenderAdapter adapter = adapterWith(baseProperties(), request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("provider failure").build()));

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Your code is 123456", "HK", "req-123")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SenderStatus.FAILED);
                    assertThat(result.failureCode()).isEqualTo("SMS_HTTP_500");
                    assertThat(result.failureMessage()).doesNotContain("sms-password", "91234567", "123456", "Your code is 123456");
                })
                .verifyComplete();
    }

    @Test
    void rejectsMissingRequiredConfigWhenEnabled() {
        SmsProperties properties = baseProperties();
        properties.setBaseUrl("");
        SmsSenderAdapter adapter = adapterWith(properties, request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Hello Member", "HK", "req-123")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SenderStatus.FAILED);
                    assertThat(result.failureCode()).isEqualTo("SMS_CONFIG_BASE_URL_MISSING");
                    assertThat(result.failureMessage()).doesNotContain("sms-password", "91234567", "Hello Member");
                })
                .verifyComplete();
    }

    @Test
    void doesNotLogSmsPasswordFullPhoneOrContent() {
        SmsSenderAdapter adapter = adapterWith(baseProperties(), request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        StepVerifier.create(adapter.sendSms(new SmsMessage("91234567", "Your code is 123456", "HK", "req-123")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
                .verifyComplete();

        assertThat(listAppender.list).isNotEmpty();
        String logOutput = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);

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

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new AssertionError("Failed to parse JSON body", ex);
        }
    }
}