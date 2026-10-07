# Fund Fact Sheet Requirements Analysis

## Sources

| Source | Role |
|---|---|
| BRD for TPA ORSO Web Revamp_6.8 Fund Fact Sheet_Final Draft Version.docx (17 Sep 2026) | Baseline; comment decisions applied |
| BRD for TPA ORSO Web Revamp_9. Configurable Matrix_V0.2.xlsx | Item 6.8.3.2 (deferred) |
| APIM contract `TRPGetFundFactSheet` (downstream developer) | Downstream schema, including the no-fund response |
| Frontend mockup | Layout reference only |
| api-service at `c12d2cc4` | Conventions (investigation scripts 001-003) |

## Baseline after comment decisions

| Item | Result |
|---|---|
| Search by fund code | Removed |
| Search by fund description | Removed |
| Fuzzy search | Removed; partial, case-insensitive matching only |
| "Displayed in PDF format" | Removed; links may be HTML or other formats |
| Download filename `[fund name]_fund_fact_sheet.pdf` | Removed |
| No fact sheet information | Per fund: an empty link disables that fund's button. No funds (TAS `err.tpa.no.fund.fact.sheet`): empty list, FE shows a label |

## Requirement ownership

| # | Requirement | Owner | BFF handling |
|---|---|---|---|
| R1 | All schemes except CLP | Deferred | None this delivery (tech lead) |
| R2 | Disable when no fact sheet info | FE + BFF | Empty or invalid link omitted; empty list when TAS returns `err.tpa.no.fund.fact.sheet` |
| R3 | List funds with fact sheets, sorted by fund name ascending | TAS + BFF | TAS selects funds; BFF sorts by display name, then `seq` |
| R4 | Fund name + Download / View | BFF + FE | Display name and link chosen by `Accept-Language` |
| R5 | Uploaded file or redirect link | BFF | URL only; never served or proxied |
| R6 | IBM funds share one fact sheet | TAS | Separate rows, each with its own `seq`; no de-duplication by URL |
| R7 | JM fact sheet by scheme number | TAS | None |
| R8 | Latest version | TAS | None |
| R10 | Missing file disables button | BFF + FE | Link omitted; FE disables |
| R11 | Search by fund name | FE | Both names returned for bilingual filtering |
| R12 | Important Notes | FE | Label owned by FE; content source open |

## Frontend-only behaviour

Debounced search, bilingual filtering on both names regardless of `Accept-Language`, clear icon, summary messages, no-result state, no-fund label, button enabling, new-tab opening, Important Notes and all wording.

## Error messages

Public error text is owned by FE. Java returns error codes only. No message configuration is added for this feature; removal of the existing message resolver is a separate, later refactor.
