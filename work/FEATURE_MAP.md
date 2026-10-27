# GlassBill feature map

Status: RE synthesis complete on 2026-10-27. This is the authoritative product boundary for the clean-room implementation. It captures independently verified behaviour and deliberately makes new design choices where the source application's implementation is either remote, runtime-only, or unsuitable to copy.

## Clean-room contract

GlassBill is a new native Android accounting, invoicing, inventory, and GST-oriented business application. It uses Kotlin and Jetpack Compose with application id `com.glassbill`, minSdk 29, and compile/target SDK 36.

- Do not reuse target source code, source text, artwork, layouts, package names, database table names, API paths, or brands.
- Evidence reports establish concepts and behaviour only. Names, UI copy, schemas, IDs, visual assets, network protocols, defaults, and migrations are original GlassBill work.
- The GPL visual reference may be studied for interaction behaviour, but its code and assets are not inputs to this implementation. GlassBill's glass theme is an original Compose implementation using Apache-licensed dependencies.
- A claim without an **Observed** basis below stays out of the initial app or is visibly labelled as a GlassBill design decision.

## Product definition

The offline-first core is one business workspace with firms, parties, item inventory, taxes, vouchers, and reports. It is intentionally useful without a login, subscription, server connection, third-party payment account, or cloud account.

The app shell has five primary destinations:

| Destination | Initial responsibility |
|---|---|
| Dashboard | cash and credit summary, recent activity, shortcuts, low-stock and due-date signals |
| Items | item/service catalogue, categories, units, tax rate, stock availability |
| Parties | customers, suppliers, groups, opening balance, statement entry point |
| Bills | sales, purchases, receipts, payments, and their searchable history |
| Reports | local financial, party, stock, and tax summaries; advanced reports arrive by milestone |

Settings, company switching, backup/export, and profile management are secondary routes rather than primary tabs.

## Evidence quality and traceability

| Dimension report | State | GlassBill consequence |
|---|---|---|
| `db-schema.md` | independently verified before handoff | two-tier storage concept; master, party, item, voucher, tax, reporting concepts |
| `txn-model.md` | independently verified before handoff | voucher families, header/line/payment separation, conversion and settlement concepts |
| `reports-tax.md` | independently verified before handoff | report registry, filters, local GST/stock/party calculation scope |
| `print-export.md` | independently verified before handoff | local print/PDF/export needs and remote boundary |
| `server-api.md` | independently verified before handoff | explicit client/server ownership boundary |
| `monetization.md` | rerun and confirmed 2026-10-27 | keep M1–M5 usable without entitlement; model future entitlement separately from staff permissions |
| `sync-backup.md` | rerun and confirmed 2026-10-27 | local backup first; sync is a future GlassBill-owned protocol |
| `dart-ui.md` | rerun and confirmed 2026-10-27 | use native Compose; exclude the separate advertising/creative sub-product |
| `masters-settings.md` | rerun and confirmed 2026-10-27 | masters, typed settings, numbering, and permissions roadmap |

The handoff mentioned a `gapfill.md`, but no such file exists in the checked workspace. It is not treated as evidence.

## Data architecture

### Storage topology

**Observed concept:** a small registry locates independent company data stores, allowing company switching and per-company backup.  
**GlassBill design:**

- `registry.db` is a Room database containing `CompanyRecord` metadata: stable UUID, display name, creation/last-opened timestamps, and storage-file identifier.
- Each company has an independently named Room database file, derived from a UUID rather than its display name. It contains all business data for that company.
- `CompanyStore` opens one company database at a time; repository APIs receive a company context and cannot accidentally cross company boundaries.
- A GlassBill archive will contain a versioned manifest plus the selected company database. The source archive format is not known and will not be imitated.

### M1 persistent model

| Aggregate | Minimum records | M1 rules |
|---|---|---|
| Company and business profile | company registry, business profile | one active company; business name, contact/address, tax registration and locale are editable |
| Preferences and series | typed preferences, document series | persist a small typed settings set plus an extensible key/value adapter; number series are scoped by document kind |
| Party | party, party group, address, payment term | a party is customer, supplier, or both; support opening balance and due terms |
| Item catalogue | item/service, category, unit, unit conversion, tax rate | goods and services share one catalogue; primary/secondary unit conversion requires a positive rate |
| Tax | tax rate and tax treatment | store original GlassBill tax records, inclusive/exclusive mode, and a rate snapshot on documents |
| Voucher | document header, line, settlement | one document header with immutable document-kind enum, line items, totals, status, and optional payment allocation |
| Inventory | stock movement | derive available stock from item movements; do not make a mutable item-total the only source of truth |
| Audit/recovery | timestamps, soft deletion journal | write create/update/delete metadata; recycle-bin UX follows later when voucher scope is present |

M1 intentionally omits the source's breadth: staff accounts, attachments, multiple warehouses, batches/serials, custom fields, currency conversion, loyalty, manufacturing, and e-invoice metadata. Each can be added through a new GlassBill migration, not by importing a source schema.

### Financial behaviour

- A sale or purchase stores a header, zero or more lines, discounts, taxes, round-off, paid amount, balance amount, and due date.
- A receipt or payment settles a party balance and may allocate against one or more open documents.
- Each document has a per-company, per-kind display number created atomically with the record.
- Returns, orders, estimates, delivery documents, expenses, other income, fixed assets, loans, journals, and inter-document conversion are follow-on voucher families. They do not block M1.
- M2 arithmetic is local and deterministic. Tax rate and tax treatment are copied into document lines so later master edits cannot change history.

## Masters and settings

M1 exposes a focused settings experience, not a clone of the large source preference catalog:

1. Business profile and tax registration.
2. Parties, party groups, payment terms, and opening balances.
3. Items/services, categories, units, unit conversions, tax rates, and stock opening quantities.
4. Document numbering and display preferences.
5. App appearance, including glass quality.

GlassBill settings have typed domain APIs. A persistence adapter may hold extension keys, but no source key namespace or unknown source default is carried forward. Multiple firms, staff roles, custom labels/fields, print configuration, withholding/collection taxes, reminders, and currency settings are staged behind their milestone dependencies.

## UX and visual system

`GlassBillTheme` applies liquid glass across the application rather than only to navigation:

- floating navigation bar, top bars, sheets, dialogs, sliders, and filter surfaces share an original frosted-surface component system;
- the user-selectable rendering mode is `Full`, `BlurOnly`, or `Flat`; it maps consistently to backdrop/refraction availability and performance, without exposing implementation-library terms in the product UI;
- navigation feedback uses original damped spring motion, press scaling, selection motion, and semantic/haptic feedback;
- accessibility and reduced-motion settings take priority over visual effects; Flat is the safe fallback;
- themes, visual copy, icons, and layouts are original GlassBill work. No target or GPL-reference assets are reused.

The architecture separates Android Compose presentation from pure Kotlin domain and data contracts so a later Compose Multiplatform client remains feasible.

## Capability roadmap

| Milestone | User-visible scope | Explicit exclusions at that stage |
|---|---|---|
| M1 | glass shell; company switcher; business, party, item, unit, and tax masters; local persistence; local dashboard data; company export design | vouchers, server sync, cloud backup, live GST services, paid gating |
| M2 | sale, purchase, receipt, payment; tax/discount/rounding calculation; stock movement; document numbering; bill history | remote validation, e-invoicing/e-way bills, network payment links |
| M3 | party statement, sales/purchase, stock, profitability, and initial GST summaries; filters; local PDF/print/export | server-scheduled email, server-minted public links, original templates |
| M4 | reminders, loyalty, currency, withholding/collection tax support | SMS/WhatsApp delivery without a GlassBill backend |
| M5 | manufacturing, fixed assets, bank-import design, local voice-assisted entry, recycle bin, fiscal-year actions, local backup/restore | source service protocols and source archive compatibility |

Every milestone must compile in GitHub Actions and retain green unit tests. Local validation is limited to `./gradlew testDebugUnitTest --max-workers=2` with a 1 GiB Gradle heap; never create APK/AAB artifacts on this machine.

## Reports, printing, and export

Observed evidence establishes a large report catalog, registry-driven filtering, and both local rendering and remote scheduled delivery. GlassBill adopts the reusable parts:

- a GlassBill-owned report definition registry with route, filter support, and availability state;
- pure local queries and calculations for sales/purchase, party statement, stock, simple profit/loss, and basic tax summaries;
- filters for date range, party, item/category, document status, and tax treatment;
- original invoice/report HTML or Compose-to-print rendering, local PDF/print sharing, and later spreadsheet export;
- a printer abstraction for later Bluetooth/USB/network thermal adapters.

Complex GST return exports, e-invoice/e-way documents, scheduled email, accountant sharing, public catalogue links, and payment links require a GlassBill backend or a separately licensed integration. They are represented as interfaces and unavailable states, never calls to a source service.

## Client/server boundary

| Concern | M1–M5 local position | Future integration seam |
|---|---|---|
| Master data, voucher arithmetic, inventory, local reports | fully local and offline-first | none required |
| Local backup/export and restore | versioned GlassBill archive; restore validates manifest/version before replacing data | optional encrypted cloud destination |
| Company sync and multi-user collaboration | no network implementation | `SyncPort`, a GlassBill-owned conflict model, and authenticated backend |
| Plan entitlement and trial UI | no feature gate for local scope | `EntitlementPort`; trusted result only from a GlassBill backend |
| Staff permissions | separate local policy model, initially single-owner | account/role service; never conflate with entitlement |
| Tax registration validation, e-invoice, e-way bill | show unavailable/placeholder state only | compliance-provider adapter and GlassBill server |
| Payment acceptance/KYC/payment links | out of initial product | provider adapter; no embedded source flow |
| Messaging, share links, scheduled reports | local share sheet where applicable only | GlassBill messaging/public-web/scheduler services |
| Analytics, feature flags, remote configuration | omitted by default | opt-in GlassBill telemetry/configuration |
| Creative advertising product | out of scope | none |

The observed source sync design replays server-issued database changelogs. GlassBill explicitly rejects that as a starting architecture: it would be unsafe to reproduce and has no usable protocol specification. A future design will use entity-level changes, versioned payloads, authorization, and conflict policy owned by GlassBill.

## Open questions and deliberate deferrals

- Runtime eligibility, exact prices, and feature-level entitlement matrix were not observable; no monetization rule is assumed.
- The observed backup archive and sync payload formats are not statically specified; GlassBill defines new formats.
- Runtime Flutter/native call semantics and navigation were not executed; the creative sub-product remains excluded regardless.
- Exact semantics/defaults for the source's large setting-key catalog are unverified; GlassBill defaults are product decisions.
- Tax compliance rules, local jurisdiction support, rounding policy, and statutory export requirements need a dedicated product/legal validation before claiming compliance.
- Hardware printer compatibility and bank statement import need device/file samples and an integration decision before implementation.

## M1 acceptance criteria

1. A fresh user can create, rename, switch, and reopen isolated local companies.
2. Each company maintains independent business profile, settings, parties, items, units, and tax rates.
3. Unit conversion rejects zero/negative conversion rates and prevents accidental cross-company references.
4. The UI provides the five-destination shell and all surfaces obey the selected glass quality mode with an accessible Flat fallback.
5. Domain/data modules contain no Android UI imports; Room and Compose remain at the app/data edge.
6. Repository and calculation tests run through the permitted local unit-test gate; GitHub Actions is the artifact-producing authority.

## Source reports

This synthesis depends only on the nine reports listed in the evidence table and `work/RECORD.md`. The reports retain exact static-evidence commands and their confirmation status; this file intentionally excludes target literals, implementation snippets, endpoints, and assets from the clone specification.
