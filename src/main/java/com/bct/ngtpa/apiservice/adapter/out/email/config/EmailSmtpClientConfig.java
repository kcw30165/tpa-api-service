package com.bct.ngtpa.apiservice.adapter.out.email.config;

import com.bct.ngtpa.apiservice.adapter.out.email.PlainSocketSmtpClient;
import com.bct.ngtpa.apiservice.adapter.out.email.SmtpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailSmtpClientConfig {

    @Bean
    @ConditionalOnMissingBean(SmtpClient.class)
    public SmtpClient smtpClient() {
        return new PlainSocketSmtpClient();
    }
}
