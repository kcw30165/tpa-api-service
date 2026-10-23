package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.shared.config.FieldAccessResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Decides, for the PUT, whether each submitted field may be edited and whether a blank value is
 * allowed, from the field-access config of the selected account (design A.3, D-13, D-14).
 *
 * <p>The config is the item-id to access-value map cached in the authenticated session (or freshly
 * fetched on a cache miss). A submitted field is checked only when the YAML binds it to a config
 * item ({@code apimBinding.config}); submitted keys unknown to the YAML, or without that binding,
 * are not this validator's concern.
 *
 * <ul>
 *   <li>HIDDEN, READONLY, blank, unrecognised, or absent from the map: {@value #CODE_NOT_EDITABLE}.
 *   <li>EDITABLE_COM (unless {@code requiredOverride} is false) with a blank value: {@value
 *       #CODE_REQUIRED}.
 *   <li>EDITABLE_OPTION, or EDITABLE_COM with a value: accepted.
 * </ul>
 *
 * <p>Errors carry a code and the field target only. Java owns no message text (D-14): the frontend
 * renders the text from the code. Errors follow the submission order.
 */
@Component
public class PersonalInformationFieldAccessValidator {

  public static final String CODE_NOT_EDITABLE = "personalInformation.field.notEditable";
  public static final String CODE_REQUIRED = "personalInformation.field.required";

  private static final String PAGE_KEY = "personalInformation";
  private static final String SOURCE_SERVER = "SERVER";

  private final BffPagesProperties pagesProperties;

  public PersonalInformationFieldAccessValidator(BffPagesProperties pagesProperties) {
    this.pagesProperties = pagesProperties;
  }

  /**
   * @param request the submitted update; may be null
   * @param itemConfig item id to access value of the selected account; must not be null (an empty
   *     map makes every submitted bound field hidden)
   * @return the access errors in submission order, empty when every submitted field is acceptable
   */
  public List<ApiError> validate(
      UpdatePersonalInformationRequest request, Map<String, String> itemConfig) {
    Objects.requireNonNull(itemConfig, "itemConfig must not be null");
    PageSchemaProperties page = personalInformationPage();
    if (page == null || request == null || request.fields() == null || request.fields().isEmpty()) {
      return List.of();
    }
    Map<String, FieldProperties> fieldsById = fieldsById(page);
    List<ApiError> errors = new ArrayList<>();
    for (Map.Entry<String, Object> submitted : request.fields().entrySet()) {
      FieldProperties field = fieldsById.get(submitted.getKey());
      String configItemId = configItemId(field);
      if (configItemId == null) {
        continue;
      }
      String access = itemConfig.get(configItemId);
      if (FieldAccessResolver.isHidden(access) || FieldAccessResolver.isReadonly(access)) {
        errors.add(fieldError(CODE_NOT_EDITABLE, submitted.getKey()));
      } else if (FieldAccessResolver.isRequired(access, field.getRequiredOverride())
          && isBlankValue(submitted.getValue())) {
        errors.add(fieldError(CODE_REQUIRED, submitted.getKey()));
      }
    }
    return List.copyOf(errors);
  }

  private PageSchemaProperties personalInformationPage() {
    if (pagesProperties == null || pagesProperties.getPages() == null) {
      return null;
    }
    return pagesProperties.getPages().get(PAGE_KEY);
  }

  private static Map<String, FieldProperties> fieldsById(PageSchemaProperties page) {
    Map<String, FieldProperties> fields = new LinkedHashMap<>();
    FormMetadataProperties form = page.getForm();
    if (form == null || form.getSections() == null) {
      return fields;
    }
    for (SectionProperties section : form.getSections()) {
      if (section == null || section.getFields() == null) {
        continue;
      }
      for (FieldProperties field : section.getFields()) {
        if (field != null && field.getId() != null && !field.getId().isBlank()) {
          fields.put(field.getId(), field);
        }
      }
    }
    return fields;
  }

  private static String configItemId(FieldProperties field) {
    if (field == null) {
      return null;
    }
    ApimBindingProperties binding = field.getApimBinding();
    if (binding == null || binding.getConfig() == null || binding.getConfig().isBlank()) {
      return null;
    }
    return binding.getConfig();
  }

  private static boolean isBlankValue(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof CharSequence text) {
      return text.toString().isBlank();
    }
    return value instanceof Collection<?> collection && collection.isEmpty();
  }

  private static ApiError fieldError(String code, String fieldId) {
    return ApiError.field(code, null, List.of(fieldId), SOURCE_SERVER);
  }
}
