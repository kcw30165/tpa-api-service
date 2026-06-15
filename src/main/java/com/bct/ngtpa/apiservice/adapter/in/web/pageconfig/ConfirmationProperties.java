package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmationProperties {

    private String afterLabelCode;

    private String beforeLabelCode;

    private String reviewMessageCode;

    private String titleCode;

    private Boolean enabled;
    private Map<String, String> title;
    private Map<String, String> reviewMessage;
    private Map<String, String> beforeLabel;
    private Map<String, String> afterLabel;
    private Map<String, Object> securityVerification;
    private Map<String, Object> actions;
    private Map<String, String> message;
}
