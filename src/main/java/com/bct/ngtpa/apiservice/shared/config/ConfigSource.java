package com.bct.ngtpa.apiservice.shared.config;

import java.util.Locale;
import java.util.Optional;

public interface ConfigSource {

    ConfigCategory category();

    Optional<String> get(String key, Locale locale);
}