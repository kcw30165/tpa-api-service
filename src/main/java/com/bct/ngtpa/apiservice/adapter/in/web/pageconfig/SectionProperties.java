package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SectionProperties {

    private String titleCode;

    private String id;
    private Map<String, String> title;
    private List<FieldProperties> fields;
}
