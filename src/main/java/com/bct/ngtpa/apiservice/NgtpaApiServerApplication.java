package com.bct.ngtpa.apiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NgtpaApiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NgtpaApiServerApplication.class, args);
    }
}
