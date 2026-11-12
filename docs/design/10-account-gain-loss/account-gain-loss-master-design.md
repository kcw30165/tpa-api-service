# Account Gain/Loss Master Design

Flow: selected-account context, ReferenceDate, shared GeneralMetadataResolver for NET_ASSET_VALUE/PRECISION, TRPGetInvestGL, strict validation, exact BigDecimal sum, localization/order, exact precision rendering, MutationResponse.

Reuse the shared APIM executor, metadata cache/resolver, language behavior, and Account Balance exact renderer. Create no duplicate metadata or transport infrastructure.
