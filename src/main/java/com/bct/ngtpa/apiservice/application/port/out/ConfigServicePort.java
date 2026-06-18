package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.dto.ConfigUpsertCommand;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Outbound port for the Config Service.
 * Returns application-facing domain records; HTTP DTOs do not cross this boundary.
 */
public interface ConfigServicePort {

    Mono<List<ConfigEntry>> listConfigs(ConfigQuery query);

    Mono<ConfigEntry> upsertConfig(ConfigUpsertCommand command);

    Mono<Void> deleteConfig(String application, String profile, String label, String configKey);
}
