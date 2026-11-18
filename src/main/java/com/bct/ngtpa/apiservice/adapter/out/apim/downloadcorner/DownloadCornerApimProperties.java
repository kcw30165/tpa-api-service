package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apim.download-corner")
public class DownloadCornerApimProperties {

  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void validate() {
    if (path == null || path.isBlank()) {
      throw new IllegalStateException("apim.download-corner.path must not be blank");
    }
    path = path.trim();
  }
}
