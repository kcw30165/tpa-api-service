package com.bct.ngtpa.apiservice.adapter.out.apim;

final class ApimPersonalInformationResponseFixtures {

  static final String SUPPLIED_TRP_GET_MEMBER_INFO_RESPONSE =
      """
      {"response":{"err-message":"","data":[{"config":[
        {
          "item-id": "check-box-apply-all-member",
          "item-name": "Check box of apply all member",
          "item-type": "UI",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "check-box-of-same-as-residential-address",
          "item-name": "Check box of Same as Residential Address",
          "item-type": "UI",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "addr1",
          "item-name": "Residential Address 1",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_COM"
        },
        {
          "item-id": "addr2",
          "item-name": "Residential Address 2",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "addr3",
          "item-name": "Residential Address 3",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "country",
          "item-name": "Residential Country",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_COM"
        },
        {
          "item-id": "corr-addr1",
          "item-name": "Correspondence Address 1",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_COM"
        },
        {
          "item-id": "corr-addr2",
          "item-name": "Correspondence Address 2",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "corr-addr3",
          "item-name": "Correspondence Address 3",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "corr-country",
          "item-name": "Correspondence Country",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_COM"
        },
        {
          "item-id": "business-phone",
          "item-name": "Hong Kong Business Phone (Phone Number)",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "business-phone-ext",
          "item-name": "Hong Kong Business Phone (Extension)",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "mobile-number",
          "item-name": "Hong Kong Mobile Phone",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "home-phone",
          "item-name": "Home Phone",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "other-phone",
          "item-name": "Overseas Contact (Phone Number)",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "other-phone-area",
          "item-name": "Overseas Contact (Area Code)",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "other-phone-country",
          "item-name": "Overseas Contact (Country Code)",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "other-phone-ext",
          "item-name": "Overseas Contact (Extension)",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "fax",
          "item-name": "Fax Number",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "email",
          "item-name": "Email Contact",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_COM"
        },
        {
          "item-id": "sms-language",
          "item-name": "Language Selection of SMS",
          "item-type": "DATA",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "EDITABLE_OPTION"
        },
        {
          "item-id": "important-notes-section",
          "item-name": "Important Notes Section",
          "item-type": "UI",
          "function": "INFO_UPDATE",
          "sch-type": "Any",
          "config-value": "READONLY"
        }
      ],"data":[
        {
          "addr1": "BUILDING",
          "addr2": "AAA BBB CCC",
          "addr3": "DISTRICT",
          "country": "Local",
          "corr-addr1": "",
          "corr-addr2": "",
          "corr-addr3": "",
          "corr-country": "",
          "business-phone": "",
          "business-phone-ext": "",
          "mobile-number": "94227617",
          "home-phone": "21234567",
          "other-phone": "61234567",
          "other-phone-area": "11",
          "other-phone-country": "22",
          "other-phone-ext": "",
          "fax": "31245678",
          "email": "YYPH.OJ@LH.WWN",
          "sms-language": ""
        }
      ]}]}}
      """;

  private ApimPersonalInformationResponseFixtures() {}
}
