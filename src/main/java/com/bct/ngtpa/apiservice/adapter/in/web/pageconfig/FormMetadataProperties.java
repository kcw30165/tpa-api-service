package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FormMetadataProperties {

    private String id;
    private String defaultMode;
    private List<ActionProperties> actions;
    private List<SectionProperties> sections;
}
