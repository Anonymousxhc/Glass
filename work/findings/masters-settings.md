# Masters and Settings — Vyapar (in.android.vyapar v31.0.1 vc728)

Scope: company/firm, parties, items, units, taxes, document series, and settings navigation. Findings are static and used only to define clean-room behaviour; the clone must not reuse source, wording, assets, or brands.

## Findings

- **Observed — Settings are a capability-rich key/value layer, not a fixed preference schema.** `db-schema.md` identifies `kb_settings(setting_key, setting_value)` with a unique key index. DEX keys span GST, HSN/SAC, discounts, round-off, item/party features, multi-firm, payment terms, stock, print/thermal options, transaction labels, backup, and sync.
  - Evidence: `rg -n 'kb_settings|unique_key_index' work/findings/db-schema.md`; `rg -n '^VYAPAR\\.[A-Z0-9_.]+$|^SETTING_[A-Z0-9_]+$' /tmp/dexstrings.txt`.

- **Observed — Settings have dedicated top-level sections.** The class inventory includes General, Item, Party, Taxes/GST, Transaction, Invoice Print, multi-currency, payment-reminder, terms-and-conditions, thermal printer, and settings search activities.
  - Evidence: `rg -n 'Lin/android/vyapar/settings/activities/.*;' work/dex/classes.txt`.

- **Observed — Access control applies to settings screens.** Transaction, invoice-print, tax/GST, party, and item settings perform a permission check before rendering the associated screen/fragment or route.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/settings/activities/TransactionSettingsActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/settings/activities/InvoicePrintSettingsActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/settings/activities/TaxesAndGstSettingsActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/settings/activities/PartySettingsActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/settings/activities/ItemSettingsActivity`.

- **Observed — Firm, party, and item masters feed every voucher.** The current schema has `kb_firms` for business profile and document counters, one polymorphic `kb_names` table for parties/accounts plus addresses and groups, and `kb_items` for goods/services/assemblies with tax, HSN/SAC, stock, price, code, unit, discount, and reminder fields.
  - Evidence: `sed -n '148,285p' work/findings/db-schema.md`; `rg -n 'kb_(firms|names|items)|kb_party_groups|kb_item_categories' work/findings/db-schema.md`.

- **Observed — Units support primary/secondary conversion.** `AddItemUnitMappingActivity` accepts a base unit, secondary unit, item or item list, validates a conversion rate, and protects changes by looking for existing use in line items, adjustments, and assembly definitions. The schema uses separate unit and unit-mapping tables.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/AddItemUnitMappingActivity`; `rg -n 'kb_item_units|kb_item_units_mapping' work/findings/db-schema.md`.

- **Observed — Tax setup is a master and a settings section.** The tax/GST settings screen links to `TaxActivity`; the schema has tax codes, composite tax mappings, TCS, TDS, and extra-charge tax references.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/settings/activities/TaxesAndGstSettingsActivity`; `rg -n 'kb_tax_code|kb_tax_mapping|kb_tcs_tax_rates|tds_tax_rates|kb_extra_charges' work/findings/db-schema.md`.

- **Observed — Document numbering is editable by document family.** `ChangePrefixActivity` exposes multiple prefix inputs, including delivery challan; the schema models a prefix per firm and transaction type, plus firm-level counters.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/activities/ChangePrefixActivity`; `rg -n 'kb_prefix|firm_invoice_number|firm_tax_invoice_number|firm_estimate_number' work/findings/db-schema.md`.

- **Observed — Users can customize names and print headings through settings keys.** `CustomHeaderSettingActivity` saves custom transaction names and maps a non-tax print option to a settings label. This is presentation customisation rather than a different voucher data model.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/CustomHeaderSettingActivity`.

## Clone boundary

M1 implements the company/firm, party, item, unit, tax-rate, and a small typed application-preference set. Preserve an extensible preference key/value adapter in the persistence layer, but expose typed domain settings rather than importing the original key namespace. Staff permissions, custom fields, multiple firms, printing, TCS/TDS, currencies, reminders, and advanced settings follow their milestone dependencies.

## Verification pass — 2026-10-27

The five representative settings classes, unit mapping screen, prefix screen, settings-key inventory, and schema excerpts were re-run and **CONFIRMED**. The exact runtime meaning and default value of every settings key is **UNVERIFIED** unless separately stated in a report; GlassBill will define its own defaults.
