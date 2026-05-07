package com.bct.ngtpa.apiservice;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class NgtpaApiServerApplicationTest {

    @Test
    void delegatesMainMethodToSpringApplicationRun() {
        String[] args = {"--spring.main.web-application-type=reactive"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            NgtpaApiServerApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(NgtpaApiServerApplication.class, args));
        }
    }
}