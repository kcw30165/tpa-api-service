package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RegistrationIdempotencyProperties.class)
public class RegistrationPolicyPropertiesConfig {}
