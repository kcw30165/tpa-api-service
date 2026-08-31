package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "apim.general-metadata")
public class GeneralMetadataApimProperties {
  private String path;

  public void validate() {
    if (path == null || path.isBlank())
      throw new IllegalStateException("apim.general-metadata.path must not be blank");
    path = path.trim();
  }
}
