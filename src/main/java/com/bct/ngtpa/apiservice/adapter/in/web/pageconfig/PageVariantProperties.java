package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageVariantProperties {

  private Map<String, String> title;
  private Map<String, String> body;
  private List<ActionProperties> actions;
}
