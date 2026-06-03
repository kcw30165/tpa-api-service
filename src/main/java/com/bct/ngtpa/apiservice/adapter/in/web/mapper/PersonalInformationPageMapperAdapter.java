package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.application.port.out.PersonalInformationPageMapperPort;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PersonalInformationPageMapperAdapter implements PersonalInformationPageMapperPort {

    private final PersonalInformationFieldMapper fieldMapper;
    private final BffPagesProperties bffPagesProperties;

    public PersonalInformationPageMapperAdapter(
            PersonalInformationFieldMapper fieldMapper,
            BffPagesProperties bffPagesProperties) {
        this.fieldMapper = fieldMapper;
        this.bffPagesProperties = bffPagesProperties;
    }

    @Override
    public Map<String, Object> map(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            String language) {
        return fieldMapper.map(apimData, apimConfig, bffPagesProperties, language);
    }
}
