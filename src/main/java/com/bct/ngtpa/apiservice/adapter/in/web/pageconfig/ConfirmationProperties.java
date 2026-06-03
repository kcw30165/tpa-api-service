package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;

public class ConfirmationProperties {

    private Boolean enabled;
    private Map<String, String> title;
    private Map<String, String> reviewMessage;
    private Map<String, String> beforeLabel;
    private Map<String, String> afterLabel;
    private Map<String, Object> securityVerification;
    private Map<String, Object> actions;

    /**
     * Kept for backward compatibility with the previous simple acknowledgement-style config.
     */
    private Map<String, String> message;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getTitle() {
        return title;
    }

    public void setTitle(Map<String, String> title) {
        this.title = title;
    }

    public Map<String, String> getReviewMessage() {
        return reviewMessage;
    }

    public void setReviewMessage(Map<String, String> reviewMessage) {
        this.reviewMessage = reviewMessage;
    }

    public Map<String, String> getBeforeLabel() {
        return beforeLabel;
    }

    public void setBeforeLabel(Map<String, String> beforeLabel) {
        this.beforeLabel = beforeLabel;
    }

    public Map<String, String> getAfterLabel() {
        return afterLabel;
    }

    public void setAfterLabel(Map<String, String> afterLabel) {
        this.afterLabel = afterLabel;
    }

    public Map<String, Object> getSecurityVerification() {
        return securityVerification;
    }

    public void setSecurityVerification(Map<String, Object> securityVerification) {
        this.securityVerification = securityVerification;
    }

    public Map<String, Object> getActions() {
        return actions;
    }

    public void setActions(Map<String, Object> actions) {
        this.actions = actions;
    }

    public Map<String, String> getMessage() {
        return message;
    }

    public void setMessage(Map<String, String> message) {
        this.message = message;
    }
}
