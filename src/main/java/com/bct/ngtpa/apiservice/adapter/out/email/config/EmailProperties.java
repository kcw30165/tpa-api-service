package com.bct.ngtpa.apiservice.adapter.out.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email")
public class EmailProperties {

  private boolean enabled = true;
  private Smtp smtp = new Smtp();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Smtp getSmtp() {
    return smtp;
  }

  public void setSmtp(Smtp smtp) {
    this.smtp = smtp == null ? new Smtp() : smtp;
  }

  public static class Smtp {
    private String host = "";
    private int port = 25;
    private String from = "";
    private int timeoutMillis = 5000;

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public int getTimeoutMillis() {
      return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
    }
  }
}
