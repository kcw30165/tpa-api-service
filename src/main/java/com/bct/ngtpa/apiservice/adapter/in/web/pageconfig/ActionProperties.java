package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionProperties {

  private String id;

  private String labelCode;

  private String nextStep;

  private String name;
  private String type;
  private Map<String, String> label;
}
