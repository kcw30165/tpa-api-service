
# Information Update — Extracted BRD and Config Matrix Rules

Date: 2026-06-02  
Scope: Rules related only to **Information Update / Personal Information** from BRD 6.3 and Config Matrix 6.3.

---

## 1. Function Scope and Access

### 1.1 Function purpose

The Information on Record & Update function provides members with online access to view and update their contact information, including:

- residential address
- correspondence address
- phone numbers
- email address

### 1.2 Scheme scope

- Applies to all schemes.
- Field visibility and editability are scheme-specific and driven by configuration.

### 1.3 Menu / access

- Accessible via Personal Information / profile related entry point.
- Exact UI location may depend on final layout.

---

## 2. Config Matrix Rules for Information Update

The following configurable items apply to Information Update.

### 2.1 Config value meanings

Supported config values for update fields:

```text
HIDDEN
READONLY
EDITABLE_COM
EDITABLE_OPTION
```

Approved BFF mapping:

```text
HIDDEN          -> omit field from response
READONLY        -> readonly: true, required: false
EDITABLE_COM    -> readonly: false, required: true
EDITABLE_OPTION -> readonly: false, required: false
```

### 2.2 Configurable items

| Config Item ID | Configurable Item | Expected Values | Default | Remarks |
|---|---|---|---|---|
| 6.3.1 | Check box of apply all member | HIDDEN / EDITABLE_OPTION | EDITABLE_OPTION | Progress-owned |
| 6.3.2 | Residential Address | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_COM | Progress-owned |
| 6.3.3 | Check box of Same as Residential Address | HIDDEN / EDITABLE_OPTION | EDITABLE_OPTION | Only enabled if Residential and Correspondence Address are unhidden/editable |
| 6.3.4 | Correspondence Address | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_COM | Progress-owned |
| 6.3.5 | Hong Kong Business Phone, including phone number and extension | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_OPTION | Progress-owned |
| 6.3.6 | Hong Kong Mobile Phone | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_OPTION | Progress-owned |
| 6.3.7 | Overseas Contact, including country code, area code, phone number | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_OPTION | Progress-owned |
| 6.3.8 | Fax Number | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_OPTION | Only for CLP |
| 6.3.9 | Email Contact | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_COM | Progress-owned |
| 6.3.10 | Language Selection of SMS | HIDDEN / READONLY / EDITABLE_COM / EDITABLE_OPTION | EDITABLE_OPTION | Progress-owned |
| 6.3.11 | Important Notes Section | HIDDEN / READONLY | HIDDEN | Progress-owned |
| 6.3.12 | Important Notes Message Box English | HIDDEN / READONLY | READONLY | If Important Notes Section is unhidden |
| 6.3.13 | Important Notes Message Box Chinese | HIDDEN / READONLY | READONLY | If Important Notes Section is unhidden |

---

## 3. Page and Mode Rules

### 3.1 Page headers

The Information Update sub-page header is:

```text
Personal Information
```

### 3.2 Default mode and edit mode

- The update fields can toggle between edit mode and read-only mode according to scheme configuration.
- Default mode is edit-enabled, but fields may become read-only according to scheme-specific configuration.
- For BFF response, use `readonly`, not `editable`.

### 3.3 Navigation with unsaved changes

If the member is in edit mode and attempts to access another web function/page:

English message:

```text
Unsaved changes for this page will be lost. Continue to leave this page?
```

Chinese message:

```text
此頁面的更改並未保存，是否確定繼續離開此頁面？
```

Actions:

- Yes: leave current page, go to selected function/page, and resume read-only mode.
- No: stay on current page.

### 3.4 Placeholder behavior

- Placeholder disappears once user begins typing.

---

## 4. Multiple Account / Apply-All-Member Rule

If members possess multiple accounts under the Plan:

- Display a red alert message above Submit and Reset buttons upon submission of updates.
- Checkbox is pre-ticked by default.
- Checkbox is not allowed to be unticked.
- In BFF response, represent this as normal checkbox field under `controlInformation`:

```text
applyToAllMemberAccounts
```

Recommended field behavior:

```json
{
  "value": true,
  "readonly": true,
  "required": false
}
```

---

## 5. Termination Status Rule

Members may generally update their information even if termination status is:

```text
O / P / S / T
```

For status `T`:

- Updates are allowed as long as the account remains within the web accessibility period.
- If the account can be logged into and selected in web by the member, its corresponding contact information can be updated.

---

## 6. Address Information Rules

Approved BFF section:

```text
addressInformation
```

Approved fields:

```text
residentialAddressLine1
residentialAddressLine2
residentialAddressLine3
residentialCountry
mailingAddressSameAsResidential
mailingAddressLine1
mailingAddressLine2
mailingAddressLine3
mailingCountry
```

---

## 7. Residential Address Rules

### 7.1 Data source and display

Residential Address consists of:

- 3 textboxes extracting Additional Address from database.
- 1 dropdown extracting country code from database.

Mapping:

```text
residentialAddressLine1 -> Additional Address line 1
residentialAddressLine2 -> Additional Address line 2
residentialAddressLine3 -> Additional Address line 3
residentialCountry      -> Country
```

If no residential/additional address record exists:

- Display correspondence/main address as fallback.

If no country record exists:

- Show placeholder:

```text
Country / 國家
```

### 7.2 Field attributes

- Free text including English, Traditional Chinese, numbers, and symbols.
- Compulsory by default unless Correspondence Address is entered.
- Max length: 40 characters for each address line.
- Country is mandatory when residential address is used.

### 7.3 Validation rules

If residential address is compulsory and line 1 is blank:

English:

```text
Please input a residential address.
```

Chinese:

```text
請輸入居住地址。
```

If residential address is compulsory and line 1 is less than 5 characters:

English:

```text
Please input a valid residential address.
```

Chinese:

```text
請輸入有效居住地址。
```

If the address is a PO Box address:

English:

```text
PO Box address will not be accepted, please re-enter your address as our record.For enquiry, please contact us at Member Hotline 2298XXXX .
```

Chinese:

```text
郵政信箱恕不接受，請重新輸入住址，以作資料紀錄。如欲查詢，請致電成員熱線2298 XXXX 與我們聯絡。
```

### 7.4 AEOI country-change rule

If updated country code is different from the current record:

- Submission can proceed successfully.
- AEOI warning/reminder should be displayed.
- User should be reminded to submit AEOI Self-Certificate.

---

## 8. Correspondence / Mailing Address Rules

### 8.1 Data source and display

Correspondence Address consists of:

- 3 textboxes extracting Main Address from database.
- 1 dropdown extracting country code from database.

Mapping:

```text
mailingAddressLine1 -> Main Address Building
mailingAddressLine2 -> Main Address Street
mailingAddressLine3 -> Main Address District
mailingCountry      -> Country
```

If no data exists:

- Show null.

If no country record exists:

- Show placeholder:

```text
Country / 國家
```

### 8.2 Same-as-residential checkbox

`mailingAddressSameAsResidential` behavior:

- Display only if both Residential Address and Correspondence Address are unhidden/editable in config.
- When checked, auto-populate residential address values into mailing address values.
- Textboxes and dropdown remain editable.
- When unchecked, mailing address fields remain blank.

Approved BFF copy mapping:

```text
residentialAddressLine1 -> mailingAddressLine1
residentialAddressLine2 -> mailingAddressLine2
residentialAddressLine3 -> mailingAddressLine3
residentialCountry      -> mailingCountry
```

### 8.3 Field attributes

- Free text including English, Traditional Chinese, numbers, and symbols.
- Compulsory by default unless Residential Address is entered.
- Max length: 40 characters for each address line.
- Country is mandatory when mailing address is used.

### 8.4 Validation rules

If correspondence address is compulsory and line 1 is blank:

English:

```text
Please input a correspondence address.
```

Chinese:

```text
請輸入通訊地址。
```

If correspondence address is compulsory and line 1 is less than 5 characters:

English:

```text
Please input a valid residential address.
```

Chinese:

```text
請輸入有效居住地址。
```

> Note: BRD wording says valid residential address even under Correspondence Address validation. This should be confirmed with PS if wording should become “valid correspondence address”.

---

## 9. Cross-Address Rules

Approved BFF semantic:

```text
address exists = addressLine1 is not blank
```

Rules:

1. Residential Address and Mailing Address cannot both be empty.
2. If Residential Address is used:
   - `residentialAddressLine1` must be valid.
   - `residentialCountry` must be selected.
3. If Mailing Address is used:
   - `mailingAddressLine1` must be valid.
   - `mailingCountry` must be selected.
4. Country alone does not count as an address.
5. Line 2 or line 3 alone does not count as an address.

---

## 10. Local Contact Number Rules

Approved BFF section:

```text
localContactNumber
```

Approved fields:

```text
hongKongBusinessPhone
hongKongBusinessPhoneExtension
homeTel
hongKongMobilePhone
faxNo
```

---

## 11. Hong Kong Business Telephone Number Rules

### 11.1 Phone number

Data source:

- Extract Business Phone Number from database.
- If no data, show null.

Field attributes:

- Integers only.
- Optional unless extension is entered.
- Max length: 8 characters.

Validation:

If submitted with non-integers:

English:

```text
Please input a valid Hong Kong Business Telephone Number.
```

Chinese:

```text
請輸入有效香港公司電話號碼。
```

If extension is entered but business phone number is blank:

English:

```text
Please input a valid Hong Kong Business Phone Number.
```

Chinese:

```text
請輸入有效香港公司電話號碼。
```

### 11.2 Extension

Data source:

- Extract Ext of Business Phone Number from database.
- If no data, show placeholder:

```text
Ext. / 內線
```

Field attributes:

- Integers only.
- Optional.
- Max length: 4 characters.

Validation:

If submitted with non-integers:

English:

```text
Please input a valid Ext.
```

Chinese:

```text
請輸入有效內線。
```

---

## 12. Hong Kong Home Telephone Number Rules

Data source:

- Extract Resident Phone Number from database.
- If no data, show null.

Field attributes:

- Integers only.
- Optional.
- Max length: 8 characters.

Validation:

If submitted with non-integers:

English:

```text
Please input a valid Hong Kong Home Telephone Number.
```

Chinese:

```text
請輸入有效香港住所電話號碼。
```

---

## 13. Hong Kong Mobile Phone Number Rules

Data source:

- Extract Mobile Phone Number from database.
- If no data, show null.

Field attributes:

- Integers only.
- Optional.
- Max length: 8 characters.

Validation:

If submitted with non-integer, or number does not start with 4-9:

English:

```text
Please input a valid Hong Kong Mobile Telephone Number.
```

Chinese:

```text
請輸入有效香港手提電話號碼。
```

Additional BRD note:

- Mobile is optional because email address is mandatory for OTP.
- Some schemes may allow both email and SMS OTP depending on scheme/trust-level setting.

---

## 14. Overseas Contact Rules

Approved BFF section:

```text
overseasContacts
```

Approved fields:

```text
overseasCountryCode
overseasAreaCode
overseasPhoneNumber
```

---

## 15. Overseas Phone — Country Code Rules

Data source:

- Extract Country of Overseas Phone Number from database.
- If no data, show placeholder:

```text
Country Code / 國家號碼
```

Field attributes:

- Integers only.
- Optional unless Overseas Phone Number is entered.
- Max length follows TAS setting.

Validation:

If submitted with non-integers:

English:

```text
Please input a valid Country Code.
```

Chinese:

```text
請輸入有效國家號碼。
```

If overseas phone number is entered but country code is blank:

English:

```text
Please input a valid Country Code.
```

Chinese:

```text
請輸入有效國家號碼。
```

---

## 16. Overseas Phone — Area Code Rules

Data source:

- Extract Area of Overseas Phone Number from database.
- If no data, show placeholder:

```text
Area Code / 區域號碼
```

Field attributes:

- Integers only.
- Optional.
- Max length follows TAS setting.

Validation:

If submitted with non-integers:

English:

```text
Please input a valid Area Code.
```

Chinese:

```text
請輸入有效區域號碼。
```

---

## 17. Overseas Phone — Phone Number Rules

Data source:

- Extract Phone No. of Overseas Phone Number from database.
- If no data, show placeholder:

```text
Phone number / 電話號碼
```

Field attributes:

- Integers only.
- Optional unless Country Code or Area Code is entered.
- Max length: 17 characters.

Validation:

If submitted with non-integers:

English:

```text
Please input a valid Overseas Phone Number.
```

Chinese:

```text
請輸入有效海外電話號碼。
```

If country code or area code is entered but overseas phone number is blank:

English:

```text
Please input a valid Overseas Phone Number.
```

Chinese:

```text
請輸入有效海外電話號碼。
```

---

## 18. Email Contact Rules

Approved BFF section:

```text
electronicContacts
```

Approved field:

```text
emailAddress
```

Data source:

- Extract E-mail from database.
- If no data, show null.

Field attributes:

- Free text including English, numbers, and symbols.
- Compulsory by default.
- Max length: 120 characters.

Validation:

If blank or invalid format:

English:

```text
Please provide a valid email address.
```

Chinese:

```text
請輸入有效電郵地址。
```

Invalid email format includes:

- invalid characters
- missing `@`
- starts with `@`
- contains more than one `@`
- spacing in between

---

## 19. Fax Number Rules

Approved BFF field:

```text
faxNo
```

Data source:

- Extract Fax No. / Fax Number from database.
- If no data, show null.

Field attributes:

- Optional.
- Max length follows TAS setting.
- BRD table says allow `+`, `-`, and integers only.
- BRD comment says allow integers, space, and `-`.

Recommended BFF interpretation until clarified:

```text
Allow digits, space, +, and -
```

Validation:

If submitted with invalid characters:

English:

```text
Please input a valid Fax No.
```

Chinese:

```text
請輸入有效傳真號碼。
```

---

## 20. SMS Language Selection Rules

Approved BFF section:

```text
communicationSettings
```

Approved field:

```text
smsLanguage
```

Field behavior:

- Optional.
- 2 radio buttons / options:
  - English
  - Chinese
- Extract SMS language preference from database.
- If record exists, preselect stored value.
- If no record exists, default selection is English.

Option values are not embedded in `GET /personal-information`; use separated option API / `optionSource`.

---

## 21. Important Notes Rules

Approved BFF section:

```text
importantNotes
```

Config-driven:

- Hide section if not applicable / config is hidden.
- If unhidden, show as readonly.
- English and Chinese message boxes are config-driven.
- Important Notes are scheme-specific.

Recommended BFF representation:

```text
controlType: noteList
readonly: true
required: false
```

---

## 22. AEOI Warning Message Rules

Placement:

- Display warning at the end of the Personal Information section / box.

English message:

```text
Please remember to submit your AEOI Self-Certificate by clicking Self Certification for Tax Residency function.
```

Chinese message:

```text
請記得點選『稅務居民證明』功能，以提交您的 AEOI 證明。
```

Behavior:

- Text “Self Certification for Tax Residency” should redirect to the Self Certification for Tax Residency function page.
- If member is in edit mode, clicking the hyperlink should still trigger the unsaved changes warning before navigation.

---

## 23. Reset Form Rule

When member clicks Reset Form:

- Clear all member-input values.
- Reload current data from database.

BFF action:

```json
{
  "reset": {
    "enabled": true,
    "label": "Reset"
  }
}
```

---

## 24. Submit / Full Validation Rule

After clicking Submit Changes:

- Perform full validation based on all update-field rules.
- Highlight the field returning error.
- After validation passes, show Before & After Changes popup for user review and password input.

---

## 25. Before & After Confirmation Popup Rules

Popup header:

English:

```text
Confirm Information Update
```

Chinese:

```text
確認個人資料更改
```

Review message:

English:

```text
Please review the changes you've made to your information before confirming.
```

Chinese:

```text
請在確認前檢查已更改的個人資料。
```

Sections:

- Review Your Changes
- Before Changes
- After Changes
- Security Verification

The popup should show:

- only fields that were updated
- original/before value
- latest/after value

---

## 26. Password Verification Rules

Section header:

```text
Please enter your Password to submit your request
```

Password field:

- Placeholder: `Enter your 8-16 digits Password` / later wording `Enter your password (8-16 characters)`.
- Eye icon toggles visibility.
- Default masked.
- Toggle is instantaneous and reversible.

Validation rules:

- Minimum length: 8 characters.
- Maximum length: 16 characters.
- At least 1 uppercase letter.
- At least 1 lowercase letter.
- At least 1 number.
- At least 1 special character.
- No spaces.
- Password must match record.

Confirm & Submit button:

- Enabled only when a password value has been entered.
- On click, validate password.
- If validation fails, show error alert and do not proceed.
- If validation passes, proceed to acknowledgement page.

Password error message:

English:

```text
The password is incorrect. Please try again.
- Please ensure that the password entered is correct
- Your password should be 8-16 characters long, include uppercase and lowercase letters, numbers and symbols
- Please ensure that the Caps Lock is not enabled.
- Avoid entering any extra spaces
- Repeated unsuccessful attempts may result in temporary account suspension
- If you have forgotten your password, please use the “Reset Password” function
```

Chinese:

```text
密碼不正確，請再試一次。
- 請確認輸入的密碼是否正確
- 您的密碼須為8至16個字元，並包含大小寫英文字母、數字及符號，且不可包含空格。
- 留意大小寫（CAPS LOCK）是否開啟
- 確認沒有多餘空格
- 如多次輸入錯誤，帳戶可能會被暫時鎖定
- 如忘記密碼，請使用「重設密碼」功能
```

BFF note:

- FE can use metadata for basic password hints and button enabling.
- BE remains final authority for password verification.

---

## 27. Error Display Behavior

- When validation rule is not met, display red error message under the corresponding textbox.
- Error messages are dismissed once corrected input is entered.

---

## 28. Acknowledgement Page Rules

Acknowledgement page is handled by FE and should not be included in `GET /personal-information` response.

BRD acknowledgement content includes:

- Information in Record section
- Acknowledgement section
- Submission Details section
- Review section
- Back to Main button

But for current BFF contract:

```text
Do not include acknowledgement metadata in GET response.
```

---

## 29. Notification After Update

Upon successful update:

- Send e-notifications to member for confirmation.
- Notification templates are provided separately.
- Notification may include reminder to update CRS/FATCA Self Certification if necessary.

---

## 30. Approved BFF Section Mapping Summary

```text
addressInformation
  residentialAddressLine1
  residentialAddressLine2
  residentialAddressLine3
  residentialCountry
  mailingAddressSameAsResidential
  mailingAddressLine1
  mailingAddressLine2
  mailingAddressLine3
  mailingCountry

localContactNumber
  hongKongBusinessPhone
  hongKongBusinessPhoneExtension
  homeTel
  hongKongMobilePhone
  faxNo

overseasContacts
  overseasCountryCode
  overseasAreaCode
  overseasPhoneNumber

electronicContacts
  emailAddress

communicationSettings
  smsLanguage

importantNotes
  importantNotes

controlInformation
  applyToAllMemberAccounts
```

---

## 31. Recommended Generic Cross-Field Validation Rule Model

To avoid FE hardcoding business-specific rule types, use generic executable rules:

```text
validationRules[]
  id
  type
  severity
  when
    operator
    field / conditions / groups
  then
    operator
    field / fields / targets
  code
  message
```

Recommended FE-supported `when.operator` values:

```text
notBlank
blank
changed
all
any
allGroupsEmpty
```

Recommended FE-supported `then.operator` values:

```text
fail
required
allRequired
showMessage
```

`type` should be descriptive only. FE should branch on `when.operator` and `then.operator`, not on custom business `type`.
