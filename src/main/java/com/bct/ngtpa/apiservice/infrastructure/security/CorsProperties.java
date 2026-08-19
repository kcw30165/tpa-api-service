package com.bct.ngtpa.apiservice.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cors")
@Getter
@Setter
public class CorsProperties {
  private List<String> allowedOrigins = new ArrayList<>();
  private List<String> allowedHeaders = new ArrayList<>();
  private List<String> allowedMethods = new ArrayList<>();
}
