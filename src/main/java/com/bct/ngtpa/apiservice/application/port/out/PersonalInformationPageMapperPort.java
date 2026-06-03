package com.bct.ngtpa.apiservice.application.port.out;

import java.util.Map;

public interface PersonalInformationPageMapperPort {

    Map<String, Object> map(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            String language);
}
