package com.bct.ngtpa.apiservice.adapter.out.email;

public record SmtpSendCommand(
                String host,
                int port,
                int timeoutMillis,
                String from,
                String to,
                String subject,
                String body) {
}
