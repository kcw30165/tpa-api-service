package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SenderPortContractShapeTest {

    @Test
    void smsSenderPortExposesSendSmsOperation() {
        Method sendSms = method(SmsSenderPort.class, "sendSms", SmsMessage.class);

        assertThat(sendSms.getReturnType()).isEqualTo(Mono.class);
        assertThat(sendSms.getGenericReturnType().getTypeName())
                .isEqualTo(Mono.class.getName() + "<" + SenderResult.class.getName() + ">");
    }

    @Test
    void emailSenderPortExposesSendEmailOperation() {
        Method sendEmail = method(EmailSenderPort.class, "sendEmail", EmailMessage.class);

        assertThat(sendEmail.getReturnType()).isEqualTo(Mono.class);
        assertThat(sendEmail.getGenericReturnType().getTypeName())
                .isEqualTo(Mono.class.getName() + "<" + SenderResult.class.getName() + ">");
    }

    private Method method(Class<?> portType, String name, Class<?> parameterType) {
        return Arrays.stream(portType.getMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[] {parameterType}))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + name));
    }
}