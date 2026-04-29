package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;

import java.util.List;

public record NotificationListResult(List<NoticeMessage> notifications) {}
