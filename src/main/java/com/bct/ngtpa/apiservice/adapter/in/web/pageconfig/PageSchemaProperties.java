package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageSchemaProperties {

  private Map<String, Map<String, String>> display = new LinkedHashMap<>();
  private Map<String, PageVariantProperties> variants = new LinkedHashMap<>();

  private PageMetadataProperties metadata;
  private FormMetadataProperties form;
  private List<ValidationRuleProperties> validations;
  private List<FieldGroupProperties> fieldGroups = new ArrayList<>();
  private ConfirmationProperties confirmation;
  private DownstreamErrorClassificationProperties downstreamErrorClassification;

  public void setDisplay(Map<String, Map<String, String>> display) {
    this.display = display == null ? new LinkedHashMap<>() : display;
  }

  public void setVariants(Map<String, PageVariantProperties> variants) {
    this.variants = variants == null ? new LinkedHashMap<>() : variants;
  }

  public void setFieldGroups(List<FieldGroupProperties> fieldGroups) {
    this.fieldGroups = fieldGroups == null ? new ArrayList<>() : fieldGroups;
  }
}
