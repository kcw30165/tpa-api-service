package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OptionProperties {
    private String value;
    private String labelCode;
    private Map<String, String> label;
}
