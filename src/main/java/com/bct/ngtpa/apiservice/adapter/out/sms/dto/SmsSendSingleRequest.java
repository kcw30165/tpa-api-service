package com.bct.ngtpa.apiservice.adapter.out.sms.dto;

public record SmsSendSingleRequest(
        String username,
        String password,
        String sendId,
        String classCode,
        String country,
        String phone,
        String smsContent) {
}