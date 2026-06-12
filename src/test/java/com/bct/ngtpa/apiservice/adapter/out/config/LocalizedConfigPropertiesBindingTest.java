package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedConfigPropertiesBindingTest {

    @Test
    void bindsDateAmountCurrencyAndErrorMessageRoots() throws Exception {
        var yaml = """
                date-display-format:
                  en:
                    date: dd/MM/yyyy
                    date.JP: yyyy/MM/dd
                amount-display-format:
                  en:
                    amount: '#,##0.00'
                    amount.JP: '#,##0.000'
                currency-mapping:
                  en:
                    EUR: Euro
                    EUR.TB.HKBU: Euro HKBU
                error-message:
                  en:
                    err.request.invalid: Invalid request.
                    system.unexpected: System error.
                """;

        var environment = environment(yaml);

        assertThat(bind(environment, "date-display-format").getLocaleFormats("en"))
                .containsEntry("date", "dd/MM/yyyy")
                .containsEntry("date.JP", "yyyy/MM/dd");
        assertThat(bind(environment, "amount-display-format").getLocaleFormats("en"))
                .containsEntry("amount", "#,##0.00")
                .containsEntry("amount.JP", "#,##0.000");
        assertThat(bind(environment, "currency-mapping").getLocaleFormats("en"))
                .containsEntry("EUR", "Euro")
                .containsEntry("EUR.TB.HKBU", "Euro HKBU");
        assertThat(bind(environment, "error-message").getLocaleFormats("en"))
                .containsEntry("err.request.invalid", "Invalid request.")
                .containsEntry("system.unexpected", "System error.");
    }

    @Test
    void returnsEmptyMapForBlankOrMissingLocale() {
        var properties = new LocalizedConfigProperties();
        assertThat(properties.getLocaleFormats(null)).isEmpty();
        assertThat(properties.getLocaleFormats(" ")).isEmpty();
        assertThat(properties.getLocaleFormats("missing")).isEmpty();
    }

    private static LocalizedConfigProperties bind(StandardEnvironment environment, String prefix) {
        return Binder.get(environment)
                .bind(prefix, Bindable.of(LocalizedConfigProperties.class))
                .orElseThrow(IllegalStateException::new);
    }

    private static StandardEnvironment environment(String yaml) throws Exception {
        var environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        var loader = new YamlPropertySourceLoader();
        var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
        sources.addFirst(loader.load("test", resource).getFirst());
        return environment;
    }
}
