package com.bct.ngtpa.apiservice.adapter.out.email;

import com.bct.ngtpa.apiservice.adapter.out.email.config.EmailProperties;
import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private static final String EMAIL_PROVIDER_FAILURE_MESSAGE = "Email provider request failed";
    private static final String EMAIL_CONFIGURATION_FAILURE_MESSAGE = "Email adapter configuration is incomplete";

    private final SmtpClient smtpClient;
    private final EmailProperties emailProperties;

    public SmtpEmailSenderAdapter(SmtpClient smtpClient, EmailProperties emailProperties) {
        this.smtpClient = Objects.requireNonNull(smtpClient, "smtpClient must not be null");
        this.emailProperties = Objects.requireNonNull(emailProperties, "emailProperties must not be null");
    }

    @Override
    public Mono<SenderResult> sendEmail(EmailMessage message) {
        SenderResult configurationFailure = validateConfiguration();
        if (configurationFailure != null) {
            log.warn(
                    "Email adapter configuration incomplete hostConfigured={} port={} recipientMasked={} bodyLogged=false",
                    StringUtils.hasText(smtp().getHost()),
                    smtp().getPort(),
                    EmailAddressMasker.mask(message == null ? null : message.recipient()));
            return Mono.just(configurationFailure);
        }

        if (message == null) {
            return Mono.error(new IllegalArgumentException("message must not be null"));
        }

        String recipientMasked = EmailAddressMasker.mask(message.recipient());        
        SmtpSendCommand command = toCommand(message);

        log.info(
                "Email outbound request hostConfigured={} port={} recipientMasked={} bodyLogged=false",
                smtp().getHost(),
                smtp().getPort(),
                recipientMasked);

        return smtpClient.send(command)
                .thenReturn(new SenderResult(SenderStatus.ACCEPTED, null, null, null))
                .onErrorResume(error -> {
                    log.warn(
                            "Email outbound request failed hostConfigured={} port={} fromConfigured={} recipientMasked={} subjectConfigured={} bodyLogged=false errorType={} errorMessage={}",
                            StringUtils.hasText(smtp().getHost()),
                            smtp().getPort(),
                            StringUtils.hasText(resolveFrom(message.from())),
                            recipientMasked,
                            StringUtils.hasText(message.subject()),
                            error.getClass().getSimpleName(),
                            safeErrorMessage(error));
                    return Mono.just(new SenderResult(
                            SenderStatus.FAILED,
                            null,
                            "EMAIL_REQUEST_ERROR",
                            EMAIL_PROVIDER_FAILURE_MESSAGE));
                });
    }

    private SmtpSendCommand toCommand(EmailMessage message) {
        return new SmtpSendCommand(
                smtp().getHost(),
                smtp().getPort(),
                smtp().getTimeoutMillis(),
                resolveFrom(message.from()),
                message.recipient(),
                message.subject(),
                message.body());
    }

    private String resolveFrom(String messageFrom) {
        return StringUtils.hasText(messageFrom) ? messageFrom : smtp().getFrom();
    }

    private SenderResult validateConfiguration() {
        if (!StringUtils.hasText(smtp().getHost())) {
            return new SenderResult(SenderStatus.FAILED, null, "EMAIL_CONFIG_HOST_MISSING",
                    EMAIL_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smtp().getFrom())) {
            return new SenderResult(SenderStatus.FAILED, null, "EMAIL_CONFIG_FROM_MISSING",
                    EMAIL_CONFIGURATION_FAILURE_MESSAGE);
        }
        return null;
    }

    private EmailProperties.Smtp smtp() {
        return emailProperties.getSmtp();
    }

    private String safeErrorMessage(Throwable error) {
        if (error == null) {
            return "";
        }
        return error.getMessage()
                .replace('\n', ' ')
                .replace('\r', ' ');
    }
}
