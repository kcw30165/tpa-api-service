package com.bct.ngtpa.apiservice.config.personalinformation;

import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationConfigCacheService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link PersonalInformationConfigCacheService}, the session cache of the field-access config
 * (design section 5). The service carries no Spring annotation (application layer stays
 * framework-free).
 *
 * <p>Every dependency already exists as a bean: {@code AuthenticatedSessionConfig} (session port,
 * protector), {@code AuthenticatedSessionConcurrencyConfig} (concurrency service), the shared
 * {@link TransientJsonPort}, and {@code ApplicationClockConfig} ({@link Clock}).
 */
@Configuration(proxyBeanMethods = false)
public class PersonalInformationConfigCacheApplicationConfig {

  @Bean
  PersonalInformationConfigCacheService personalInformationConfigCacheService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionConcurrencyService concurrency,
      TransientJsonPort json,
      Clock clock) {
    return new PersonalInformationConfigCacheService(sessions, protector, concurrency, json, clock);
  }
}
