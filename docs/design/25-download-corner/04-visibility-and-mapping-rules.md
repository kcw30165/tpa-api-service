# 04 — Language, Filtering and Mapping Rules

## 4.1 Rules table

| Rule | Input / condition | Result |
|---|---|---|
| Language selection | `Accept-Language` and resolved request-header context | Resolve one language with existing `RequestLanguageResolver`, exactly as Vesting does. It normalizes English to `en`, Chinese/HK Chinese to `zh_HK`, and unsupported/missing language to English (`RequestLanguageResolver.java:12-42`; `VestingScaleController.java:61-64`). |
| English category name | `category-name-eng` is missing, null, or blank | Malformed response; return HTTP 503 with `err.download-corner.retrieval.unavailable` and log the malformed field path. |
| English item fields | `item-name-eng`, `item-link-eng`, or `file-name-eng` is missing or null | Treat that field as blank. Apply the per-field fallback below; this is not malformed solely due to the missing/null field. |
| English language | English selected | Use the English category/item values. A blank English category name is malformed; a blank/missing/null English item name or filename remains blank. Java does not add labels or wording. |
| Chinese category name | Chinese selected; `category-name-chi` is missing, null, or blank | Treat the Chinese value as blank and fall back to `category-name-eng`. |
| Chinese item name | Chinese selected; `item-name-chi` is missing, null, or blank | Treat the Chinese value as blank and fall back to `item-name-eng`. |
| Chinese item link | Chinese selected; `item-link-chi` is missing, null, or blank | Treat the Chinese value as blank and fall back to `item-link-eng`; preserve the chosen string exactly. |
| Chinese file name | Chinese selected; `file-name-chi` is missing, null, or blank | Treat the Chinese value as blank and fall back to `file-name-eng`. |
| Sequence fields | `category-seq` or `item-seq` is missing, null, fractional, or outside the signed 32-bit `int` range | Malformed response; exact-integer decimals such as `10010.0` are accepted. Otherwise return HTTP 503 with `err.download-corner.retrieval.unavailable`, log the malformed field path, use reason `MALFORMED_RESPONSE`, and set `internalCode` to null. |
| Collection fields | `categories` or any category's `items` is missing or null | Malformed response; return HTTP 503 with `err.download-corner.retrieval.unavailable` and log the malformed field path. Empty arrays are valid. |
| Item inclusion | After language selection and fallback | Drop an item only when its resolved `itemLink` is blank. Never test remote existence. |
| Category ordering | Category sequence values | Ascending `category-seq`; stable on ties, preserving DS input order. |
| Item ordering | Within each category | Ascending `item-seq`; stable on ties, preserving DS input order. |
| Empty category | Category has no retained items | Keep category and return `items: []`. |
| Public shape | All successful results | Return `data.categories[].categoryName` and `items[].itemName`, `itemLink`, `fileName`; do not expose sequence values or `env`. |

The existing language resolver maps `zh` and `zh-HK` to `zh_HK` and otherwise falls back to English (`RequestLanguageResolver.java:24-42`). Vesting's mapper shows language selection and per-field fallback for a Chinese description (`VestingScaleWebMapper.java:36-45,103-113`); Download Corner applies the locked rule independently to all four bilingual field pairs.

“Blank” includes null, empty, and whitespace-only values. Fallback selects the English value as-is; it does not trim or rewrite nonblank links. A resolved link that is blank after fallback is the sole item-filter condition.

Missing or null Chinese category name, item name, link, and file name values are blank and therefore use their respective English values. Missing or null English item name, link, and file name values are also blank before per-field fallback and link filtering. Missing, null, or blank English category name is malformed.

## 4.2 Worked examples

### English

Input item:

```json
{
  "item-name-eng": "Member form",
  "item-link-eng": "https://documents.example.test/member-form.pdf",
  "file-name-eng": "member-form.pdf"
}
```

With English selected, output is:

```json
{
  "itemName": "Member form",
  "itemLink": "https://documents.example.test/member-form.pdf",
  "fileName": "member-form.pdf"
}
```

### Chinese with complete Chinese values

Input:

```json
{
  "category-name-eng": "Forms",
  "category-name-chi": "表格",
  "item-name-eng": "Member form",
  "item-name-chi": "成員表格",
  "item-link-eng": "https://documents.example.test/member-form.pdf",
  "item-link-chi": "https://documents.example.test/member-form-zh.pdf",
  "file-name-eng": "member-form.pdf",
  "file-name-chi": "member-form-zh.pdf"
}
```

With Chinese selected, use `表格`, `成員表格`, the Chinese link, and `member-form-zh.pdf`.

### Chinese with blank Chinese item name, link and file name

Input:

```json
{
  "category-name-eng": "Forms",
  "category-name-chi": "表格",
  "item-name-eng": "Member form",
  "item-name-chi": "   ",
  "item-link-eng": "https://documents.example.test/member-form.pdf",
  "item-link-chi": "",
  "file-name-eng": "member-form.pdf",
  "file-name-chi": null
}
```

With Chinese selected, the category name remains `表格`; the item name, link, and file name independently fall back to `Member form`, `https://documents.example.test/member-form.pdf`, and `member-form.pdf`. The item remains because the resolved link is nonblank.

### Missing/null English item name

Input item (English name is null; the Chinese name is also absent):

```json
{
  "item-name-eng": null,
  "item-link-eng": "https://documents.example.test/member-form.pdf",
  "file-name-eng": "member-form.pdf"
}
```

With Chinese selected, both the absent Chinese name and null English name are blank, so the resolved `itemName` is blank. This is not malformed and does not drop the item because its resolved link is nonblank. With an available Chinese item name, that Chinese value is selected instead.

### Missing English category name

Input category (the required English category name is omitted):

```json
{
  "category-seq": 1,
  "category-name-chi": "表格",
  "items": []
}
```

This is malformed at `response.data[0].categories[0].category-name-eng`; it returns HTTP 503 with `err.download-corner.retrieval.unavailable` and the malformed field path is logged. Chinese fallback does not make a required English category name optional.

### Missing item sequence

Input item (the required sequence is omitted):

```json
{
  "item-name-eng": "Member form",
  "item-link-eng": "https://documents.example.test/member-form.pdf",
  "file-name-eng": "member-form.pdf"
}
```

This is malformed at `response.data[0].categories[0].items[0].item-seq`; it returns HTTP 503 with `err.download-corner.retrieval.unavailable` and the malformed field path is logged.

### Blank link in both languages

If `item-link-chi` is whitespace and `item-link-eng` is null, empty, or whitespace, the resolved link is blank and the item is dropped. A blank name or file name alone does not drop an item.

### Empty category

Given a category with no input items, or one whose items are all dropped for blank resolved links, keep the category:

```json
{"categoryName":"Forms","items":[]}
```

### Sequence tie

Given categories in DS order `A(seq=2), B(seq=1), C(seq=1)`, stable ascending order is `B, C, A`. Given items in a category `X(seq=3), Y(seq=1), Z(seq=1)`, the result is `Y, Z, X`. Sequence values are not serialized.

The sequence examples assume numeric sort keys for illustration. Sequence values are represented as `int`; exact-integer decimal values are accepted, while missing, null, fractional, and out-of-range values are malformed. The actual DS wire representation remains **UNVERIFIED** pending confirmation.
