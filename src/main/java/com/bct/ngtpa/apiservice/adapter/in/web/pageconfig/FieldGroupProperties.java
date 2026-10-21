package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** A named set of form fields that are submitted together: all fields of the group, or none. */
@Getter
@Setter
public class FieldGroupProperties {

  private String name;
  private List<String> fields = new ArrayList<>();

  public void setFields(List<String> fields) {
    this.fields = fields == null ? new ArrayList<>() : fields;
  }
}
