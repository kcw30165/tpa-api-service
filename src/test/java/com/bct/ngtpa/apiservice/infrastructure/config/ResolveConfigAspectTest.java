package com.bct.ngtpa.apiservice.infrastructure.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import com.bct.ngtpa.apiservice.shared.config.ResolveConfig;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResolveConfigAspectTest {

    @Test
    void resolvesStaticDisplayFormatCode() {
        var resolver = new RecordingResolver(Optional.of("dd/MM/yyyy"));
        var proxy = proxy(new TestTarget(), resolver);

        assertEquals("dd/MM/yyyy", proxy.resolveDate(ConfigLookupContext.of("PROD", "RM", "", Locale.ENGLISH)));
        assertEquals(ConfigCategory.DISPLAY_DATE_FORMAT, resolver.lastRequest.category());
        assertEquals("date", resolver.lastRequest.code());
    }

    @Test
    void resolvesCurrencyCodeUsingSpel() {
        var resolver = new RecordingResolver(Optional.of("Euro"));
        var proxy = proxy(new TestTarget(), resolver);

        assertEquals(Optional.of("Euro"), proxy.resolveCurrency(
                "EUR", ConfigLookupContext.of("PROD", "TB", "HKBU", Locale.ENGLISH)));
        assertEquals(ConfigCategory.CURRENCY_MAPPING, resolver.lastRequest.category());
        assertEquals("EUR", resolver.lastRequest.code());
    }

    @Test
    void throwsWhenRequiredConfigMissing() {
        var proxy = proxy(new TestTarget(), new RecordingResolver(Optional.empty()));

        assertThrows(ConfigResolutionException.class,
                () -> proxy.resolveRequiredCurrency("EUR", ConfigLookupContext.of("", "", "", Locale.ENGLISH)));
    }

    @Test
    void returnsOptionalEmptyWhenOptionalConfigMissing() {
        var proxy = proxy(new TestTarget(), new RecordingResolver(Optional.empty()));

        assertEquals(Optional.empty(), proxy.resolveCurrency(
                "EUR", ConfigLookupContext.of("", "", "", Locale.ENGLISH)));
    }

    @Test
    void returnsNullWhenOptionalStringConfigMissing() {
        var proxy = proxy(new TestTarget(), new RecordingResolver(Optional.empty()));

        assertNull(proxy.resolveOptionalDate(ConfigLookupContext.of("", "", "", Locale.ENGLISH)));
    }

    private static TestTarget proxy(TestTarget target, RecordingResolver resolver) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new ResolveConfigAspect(resolver));
        return factory.getProxy();
    }

    static class TestTarget {

        @ResolveConfig(category = ConfigCategory.DISPLAY_DATE_FORMAT, code = "date", required = true)
        String resolveDate(ConfigLookupContext context) {
            return null;
        }

        @ResolveConfig(category = ConfigCategory.DISPLAY_DATE_FORMAT, code = "date")
        String resolveOptionalDate(ConfigLookupContext context) {
            return null;
        }

        @ResolveConfig(category = ConfigCategory.CURRENCY_MAPPING, code = "#currencyCode")
        Optional<String> resolveCurrency(String currencyCode, ConfigLookupContext context) {
            return Optional.empty();
        }

        @ResolveConfig(category = ConfigCategory.CURRENCY_MAPPING, code = "#currencyCode", required = true)
        String resolveRequiredCurrency(String currencyCode, ConfigLookupContext context) {
            return null;
        }
    }

    private static final class RecordingResolver implements ConfigVariantResolver {

        private final Optional<String> response;
        private ConfigLookupRequest lastRequest;

        private RecordingResolver(Optional<String> response) {
            this.response = response;
        }

        @Override
        public Optional<String> resolve(ConfigLookupRequest request) {
            this.lastRequest = request;
            if (request.required() && response.isEmpty()) {
                throw new ConfigResolutionException("missing required config");
            }
            return response;
        }
    }
}EOF_RESOLVE_CONFIG_ASPECT_TEST

rm -f src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/DisplayFormatConfigSource.java
rm -f src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/DateDisplayFormatConfigSource.java
rm -f src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/AmountDisplayFormatConfigSource.java
rm -f src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/DisplayFormatKeyCandidateStrategy.java
rm -f src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/CurrencyMappingKeyCandidateStrategy.java
rm -f src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/ConfiguredErrorMessageResolver.java

echo "008 display category split applied. Review with:"
echo "git diff -- src/main/java/com/bct/ngtpa/apiservice/shared/config/ConfigCategory.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/DateFormatConfigSource.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/AmountFormatConfigSource.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/DisplayFormatConfigSource.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/ConfigBackedAmountDisplayAdapter.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/ConfigBackedDateDisplayAdapter.java src/main/java/com/bct/ngtpa/apiservice/infrastructure/config/ConfigVariantResolverConfiguration.java src/test/java/com/bct/ngtpa/apiservice/adapter/out/config/DefaultConfigVariantResolverTest.java src/test/java/com/bct/ngtpa/apiservice/adapter/out/config/ConfigAdapterReflectionBranchProbeTest.java src/test/java/com/bct/ngtpa/apiservice/infrastructure/config/ResolveConfigAspectTest.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/DisplayFormatKeyCandidateStrategy.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/CurrencyMappingKeyCandidateStrategy.java src/main/java/com/bct/ngtpa/apiservice/adapter/out/config/ConfiguredErrorMessageResolver.java"
