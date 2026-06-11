package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMessageConfigurationBindingTest {

//     @Test
//     void bindsLocaleMapsDirectlyUnderErrorMessagePrefix() throws IOException {
//         var yaml = """
//                 error-message:
//                   en:
//                     "err.request.invalid": "Invalid request."
//                     "err.apim.service.unavailable.JP.JPM.OE": "JP JPM OE unavailable."
//                   zh_HK:
//                     "err.request.invalid": "請求無效。"
//                 """;

//         var environment = new StandardEnvironment();
//         var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
//         var propertySources = new YamlPropertySourceLoader().load("test", resource);
//         propertySources.forEach(environment.getPropertySources()::addFirst);

//         var properties = Binder.get(environment)
//                 .bind("error-message", Bindable.of(ErrorMessageProperties.class))
//                 .orElseThrow();

//         assertThat(properties.find("en", "err.request.invalid"))
//                 .contains("Invalid request.");
//         assertThat(properties.find("en", "err.apim.service.unavailable.JP.JPM.OE"))
//                 .contains("JP JPM OE unavailable.");
//         assertThat(properties.find("zh_HK", "err.request.invalid"))
//                 .contains("請求無效。");
//     }
}
