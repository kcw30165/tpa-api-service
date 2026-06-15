package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionProperties {

    private String labelCode;

    private String name;
    private Map<String, String> label;
}
