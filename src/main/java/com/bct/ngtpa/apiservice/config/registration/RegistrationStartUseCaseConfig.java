package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationCsrfGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationMessageGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationSessionGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationStartUseCaseConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationStartValidator registrationStartValidator() {
        return new RegistrationStartValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationRequestHashGenerator registrationRequestHashGenerator() {
        return (RegistrationIdempotencyAction action, List<String> normalizedParts) -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(action.name().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                for (String part : normalizedParts == null ? List.<String>of() : normalizedParts) {
                    digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
                return new RegistrationRequestHash(HexFormat.of().formatHex(digest.digest()));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("Registration request hash algorithm unavailable", exception);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(StartRegistrationUseCase.class)
    public StartRegistrationUseCase startRegistrationUseCase(
            RegistrationStartValidator registrationStartValidator,
            RegistrationRequestHashGenerator requestHashGenerator,
            RegistrationIdempotencyPort registrationIdempotencyPort,
            RegistrationLookupPort registrationLookupPort,
            RegistrationSessionGateway registrationSessionGateway,
            RegistrationCsrfGateway registrationCsrfGateway,
            RegistrationOtpGateway registrationOtpGateway,
            RegistrationMessageGateway registrationMessageGateway,
            Clock clock) {
        return new StartRegistrationService(
                registrationStartValidator,
                requestHashGenerator,
                registrationIdempotencyPort,
                registrationLookupPort,
                registrationSessionGateway,
                registrationCsrfGateway,
                registrationOtpGateway,
                registrationMessageGateway,
                clock);
    }
}
