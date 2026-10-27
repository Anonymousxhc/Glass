# reports-tax — report catalog + GST/tax engine map

Target: `in.android.vyapar` v31.0.1 (vc728). Datasets: `/home/ubuntu/mkqt/vyp/work/dex/{classes.txt,manifest.txt,raw/classes*.dex}`,
`/home/ubuntu/mkqt/vyp/work/apks/base.apk` (via `work/venv/bin/droidasc`), `/home/ubuntu/mkqt/vyp/work/jadx/sources/` (partial).
All commands were run from `/home/ubuntu/mkqt/vyp/work` unless noted. Clean-room note: names below are *observations of
behaviour/shape*, to be re-implemented independently — no code or asset reuse.

---

## 0. Layer correction relevant to this dimension (important for the clone plan)

The assignment assumed Flutter (`libapp.so`) owns the UI layer. **Reports and the whole GST/tax engine are NOT in the Dart layer.**

* `work/dart_out/scripts.jsonl` → all first-party Dart scripts are `package:adkrity_app/main-module/...`
  (158 scripts: auth, ads, orders, campaigns, WhatsApp integration, UPI/PG). `grep` of `.url` for `report` → **0 hits**.
* `work/dart_out/string_refs.jsonl` → `jq -r '.value' | grep -icE 'cgst|igst|gstin'` → **0**.
* Therefore the entire report + GST surface lives in **dex**: legacy Java (`in.android.vyapar.*`, obfuscated helper packages)
  plus a growing **Kotlin/Compose Multiplatform** layer whose FQNs appear as strings (`vyapar.shared.*`: e-invoice/e-waybill DTOs,
  report schedule models, `vyapar.shared.legacy.taxCode.bizLogic`, `vyapar.shared.presentation.einvoice.ui.viewmodel`,
  792 `vyapar.shared.` string hits total, 472 `composeResources/vyapar.shared.generated.resources/` entries in `base.apk`).
* The Flutter engine in this APK is a *sub-product* (AdKrity: ad creation / UPI collections), reached through
  method-channel/`vyaparNetwork` paths, not the billing UI.

Clone implication: a single-language (Dart/Kotlin) implementation of reports+GST is enough; no need to mirror a Flutter UI layer
for this feature area.

---

## 1. Master report catalog (the highest-value artifact)

Reports are enumerated by one Kotlin **enum** (dex name `Ls2k;`, classes3.dex). 68 constants = **8 section headings + 60 reports**.
Constant shape:

```
s2k(name, ordinal, reportTitleStringId /*vbj.* resource*/, reportType /*int id used by APIs/schedule*/,
    pricingResourceItem /*g3k.* = premium/permission resource*/, cls /*target Activity*/,
    keyValuePair /*HashMap<String,Any> launch extras*/, isHeading, isVisible, isAccessAllowed)
```

Observed full registry (from `droidasc getclass apks/base.apk "Ls2k;" | grep '= new s2k('`), grouped by the heading rows
that precede them:

**TRANSACTION_HEADER** — `SALE_REPORT`(type 4 → `reports.salePurchaseExpense.presentation.SalePurchaseExpenseReportActivity`,
extras `_report_type=4`, `_Txn_Type_list=[1,21]`, `source=reports_panel`), `PURCHASE_REPORT`(45, same activity,
`_Txn_Type_list=[2,23]`), `DAY_BOOK_REPORT`(24), `CUSTOM_REPORT`(8 → `in.android.vyapar.CustomizedReport`, extra `source=reports`),
`BILL_WISE_PROFIT_REPORT`(53), `PROFIT_AND_LOSS_REPORT`(17), `SALE_AGING_REPORT`(46), `PURCHASE_AGING_REPORT`(63, both →
`reports.aging.presentation.SalePurchaseAgingReportActivity`), `CASH_FLOW_REPORT`(1), `BALANCE_SHEET_REPORT`(51),
`TRIAL_BALANCE_REPORT`(70 → routed to `in.android.vyapar.m2dFeatures.M2DFeaturesActivity` with `m2d_feature_screen=TRIAL_BALANCE_REPORT`,
i.e. upsell/locked route).

**PARTY_HEADER** — `PARTY_STATEMENT`(9), `PARTY_WISE_PROFIT_REPORT`(54), `PARTY_REPORT`(12, "All Parties Report"),
`PARTY_REPORT_BY_ITEM`(20), `SALE_PURCHASE_AMOUNT_BY_PARTY_REPORT`(48), `PARTY_GROUP_SALE_PURCHASE_REPORT`(21).

**GST_REPORT_HEADER** — `GSTR_1_REPORT`(27), `GSTR_2_REPORT`(28), `GSTR_3B_REPORT`(29), `GSTR_DETAIL_REPORT`(64,
`GSTRTxnReportActivity`; the only entry whose last flag set differs → `0,0,0,0,1,0`), `GSTR_4_REPORT`(34),
`GSTR_9_REPORT`(43), `GSTR_9A_REPORT`(44), `SALE_SUMMARY_BY_HSN_REPORT`(56), `HSN_SAC_REPORT`(64 → `HsnOrSacReportActivity`).

**ITEM_STOCK_REPORT_HEADER** — `ITEM_SUMMARY_REPORT`(13, "Stock Summary"), `ITEM_REPORT_BY_PARTY`(11),
`ITEM_WISE_PROFIT_LOSS_REPORT`(25), `LOW_STOCK_SUMMARY_REPORT`(35, same Activity as ITEM_SUMMARY + extras),
`ITEM_DETAIL_REPORT`(14), `STOCK_DETAIL_REPORT`(42), `ITEM_CATEGORY_SALE_PURCHASE_REPORT`(22),
`ITEM_CATEGORY_STOCK_SUMMARY_REPORT`(23), `ITEM_STOCK_TRACKING_REPORT`(36, title resource `batch_report`),
`SERIAL_TRACKING_REPORT`(49), `ITEM_WISE_DISCOUNT`(55), `MANUFACTURING_ITEM_REPORT`(59) and
`CONSUMPTION_ITEM_REPORT`(60) (both → `ManufacturingReportActivity`), `STOCK_TRANSFER_REPORT`(66, `isVisible` row differs).

**BUSINESS_STATUS_HEADER** — `BANK_STATEMENT_REPORT`(10), `DISCOUNT_REPORT`(16).

**TAXES_STATUS_HEADER** — `TAX_REPORT`(15 → `in.android.vyapar.TaxReport`), `TAX_RATE_REPORT`(26 → `TaxRateReportActivity`),
`TCS_27EQ_REPORT`(58) and `TCS_RECEIVABLE_REPORT`(57) (both → `in.android.vyapar.tcs.reports.TcsReport`),
`TDS_PAYABLE_REPORT`(68) and `TDS_RECEIVABLE_REPORT`(69) (both → `reports.tds.ui.TdsReportActivity` + extras),
`VAT_201_RETURN_REPORT`(65 → `reports.vat.presentation.Vat201ReturnActivity`).

**EXPENSE_REPORT_HEADER** — `EXPENSE_REPORT`(7), `EXPENSE_CATEGORY_REPORT`(18), `EXPENSE_ITEM_REPORT`(19).

**SALE_PURCHASE_ORDER_REPORT_HEADER** — `ORDER_TXN_REPORT`(37), `ORDER_ITEM_REPORT`(38).

**OTHER_INCOME_REPORT_HEADER** — `OTHER_INCOME_REPORT`(39), `OTHER_INCOME_CATEGORY_REPORT`(40), `OTHER_INCOME_ITEM_REPORT`(41).

**LOAN_REPORTS_HEADER** — `LOAN_STATEMENT_REPORT`(52).

Supporting counts: 20 `in/android/vyapar/reports/<feature>` sub-packages in `classes.txt`
(`aging balanceSheet billWiseProfitAndLoss cashflow dayBookReport gstr hsnorsac outstandingTxnDetails partyStatement
partyWiseProfitLoss profitAndLoss reportsUtil salePurchaseByParty salePurchaseExpense scheduleReports
stockAndLowStockSummary stockDetails summaryByHsnReport tds vat stockTransfer`); 54 registered components whose manifest name
matches `report` (case-insensitive) in `dex/manifest.txt`.

UI label set (from `unzip -p apks/base.apk resources.arsc | strings`) confirms the same catalog plus GST-specific rows:
`GSTR 1 CDN`, `GSTR 1 Sales`, `GSTR 2 CDN`, `GSTR 2 Purchases`, `GSTR Transaction Report`, `Sale Summary by HSN Report`,
`SAC Report`, `TDS Payable Report`, `TDS Receivable Report`, `TCS Report`, `Trial Balance Report`, `Custom Report`,
`Favorite Reports`, `Most Used Reports`, `This is a Premium Report`.

---

## 2. Report engine plumbing (clone must reproduce these 5 pieces)

1. **Registry-driven navigation**: one enum → Activity class + extras bundle (`keyValuePair`). Several Activities are
   *shared* by two reports with different extras (Sale/Purchase/Expense all use `SalePurchaseExpenseReportActivity`
   with `_report_type` + `_Txn_Type_list`; aging uses one Activity for sale & purchase; `ItemSummaryReportActivity`
   serves stock-summary & low-stock; `TcsReport` and `TdsReportActivity` each serve two).
2. **Sync gate**: every report Activity extends `in.android.vyapar.AutoSyncBaseReportActivity`
   (e.g. `SummaryByHsnReportActivity extends AutoSyncBaseReportActivity implements nj9`), i.e. reports force a DB sync
   window before computing.
3. **Shared filter model**: `in.android.vyapar.reports.reportsUtil.model.ReportFilter(
   type: h98, label, List, List, s98, boolean)` with `h98` = FilterType enum, 14 values observed:
   `FIRM TXN_TYPE PARTY TXN_STATUS EXPENSE_CATEGORY EXPENSE_TYPE URP_USERS ITEM_CATEGORY ITEM_STATUS BANK_NAME VIEW
   PARTY_GROUP ITEM_STOCK ZERO_VAL_TXN` (+ `TAX_TYPE` field observed in the enum declaration). Filters are also modelled in
   `reports.reportsUtil.model.{ReportFilter,SelectionItem,AdditionalFieldsInExport}` and a Compose
   `vyapar.shared.presentation.multiFilterBottomSheet`.
4. **Render/export**: reports are rendered as **HTML strings → WebView/PDF** (huge `<table class='borderBottomForTxn'…>`
   literals in the dex pool) and as **Excel via Apache POI** (`org.apache.poi.hssf.usermodel.HSSFWorkbook` in
   `GSTR1ReportActivity.P0()`, `wkk.n0("GSTR1","Excel")`, `wkk.m0("Sale summary by HSN")`), plus **JSON** for GSTR
   (`menu` item visibility `tc8.a("can_export_json_for_gstr", false)`). Resource keys: `share_excel`,
   `export_operation_share_excel`, `report_export_include_aging_graph`, `report_export_include_invoice_details`,
   `report_export_date_time`.
5. **Gating**: enum carries `pricingResourceItem (g3k)` + `isAccessAllowed`; strings `isReportPremium`,
   `label_this_is_a_premium_report`, `label_premium_report_locked_message`,
   `label_premium_report_export_trial_message`, `premium_report_setting`, `urpResourceForReportType`,
   `isReportAccessibleForReportType`; copy "Access to this Report is locked. Please upgrade to Premium…".
6. **Scheduling/sharing (server-side)**: `reports.scheduleReports.{ReportScheduleActivity,ReportScheduleModel(id=reportType,
   email, frequency)}` + `vyapar.shared.domain.models.report.{ReportScheduleModel,ReportScheduleFrequency}` +
   `SyncReportSchedulesUseCase` hitting `api/ns/scheduled-reports`; frequencies observed as resource strings
   `report_schedule_weekly_subtitle`, `report_schedule_monthly_subtitle` and enum default `w3k.WEEKLY`
   (`in.android.vyapar.reports.scheduleReports.ReportScheduleModel` decompiles to `frequency = w3k.WEEKLY.getId()`).
   "Share with CA" = upload + notify: `ShareReportUploadFormRequest(fileType=…)`, `ShareReportUploadFormData(uuid=…)`
   via `/api/ns/analytics/dump/v2`, then `api/ns/gstr/ca-details/form` and `api/ns/gstr/send/wa-email`.
   No local `create table` for schedules was found in the dex pool (searched `create table.*schedul`) — schedule state is
   server-owned and mirrored into db only as cache (`%Update report schedules deleted in db`).

Representative Activity behaviour (getclass):
* `SummaryByHsnReportActivity` — holds a `lsm` filter-state (`MutableStateFlow o9q`), builds a query object via
  `px1.R0(56, saleQuery, purchaseQuery)` (56 = its `reportType`), and dispatches to `uy0` export helper with mode chosen from
  enum `ism` (`…e0(...)` PDF/print, `Y(...)` Excel ("Sale summary by HSN"), `Q(...)` another export). It groups
  `kb_lineitems` by `I.item_hsn_sac_code` (SQL block below) into **sale vs purchase summary by HSN/SAC**.
* `TaxRateReportActivity` — emits an HTML table whose header is literally
  `Sl No. | Tax name | Rate | Sale Taxable Amount | Total Tax In | Purchase/Expense Taxable Amount | Total Tax Out`
  plus a `Total Tax` footer row → per-tax-code in/out summary.
* `GSTR1ReportActivity` (jadx, 1065 lines) — date-range + `additional_cess` column toggles, HTML build, POI workbook,
  JSON export gated by `can_export_json_for_gstr`.
* `HsnOrSacReportActivity` — uses `ReportFilter` chips (`FIRM`, …) + `a7b` state, `HsnOrSacReportDbModel` rows.

---

## 3. GST / tax data model (SQLite, owned by dex)

```
create table kb_tax_code(tax_code_id integer primary key autoincrement, tax_code_name varchar(32) unique,
  tax_rate double, tax_code_type integer default 0, tax_rate_type integer default 4,
  tax_code_date_created datetime, tax_code_date_modified datetime)
create table kb_tax_mapping(tax_mapping_id integer primary key autoincrement,
  tax_mapping_group_id integer references kb_tax_code(tax_code_id),
  tax_mapping_code_id integer references kb_tax_code(tax_code_id), ...)
create table kb_tcs_tax_rates (tcs_tax_id integer primary key autoincrement,
  tcs_tax_name varchar(50) NOT NULL unique, tcs_tax_percentage double default 0,
  tcs_tax_nature_of_collection_id integer default 1, ...)
create table tds_tax_rates (id ..., name, percentage double not null, section_id integer not null, ...)
```
Item / voucher tax columns (`kb_items`, `kb_lineitems`, `kb_transactions`):
`item_hsn_sac_code varchar(32)`, `item_tax_id → kb_tax_code`, `item_tax_type integer default 2`,
`item_tax_type_purchase`, `item_tax_type_wholesale_price`, `item_additional_cess_per_unit`;
`lineitem_tax_id`, `lineitem_tax_amount`, `lineitem_additional_cess`, `lineitem_itc_applicable`, `lineitem_free_quantity`;
`txn_tax_id`, `txn_tax_percent`, `txn_tax_amount`, `txn_tax_inclusive integer default 2`, `txn_reverse_charge integer default 0`,
`txn_itc_applicable`, `txn_tds_tax_id → tds_tax_rates(id)`, `txn_tds_tax_amount`, `txn_tcs_tax_id → kb_tcs_tax_rates`,
`txn_tcs_tax_amount`, `txn_round_off_amount`, `txn_ac1/2/3_amount` + `txn_ac{1,2,3}_tax_amount` + `ac{1,2,3}_itc_applicable`,
`txn_eway_bill_number varchar(50)`, `txn_eway_bill_api_generated`, `txn_eway_bill_generated_date`, `txn_irn_number varchar(256)`.
Party/firm GST identity columns: `name_gstin_number varchar(32)`, `name_verified_gstin integer`, `firm_gstin_number`,
`name_tin_number` (VAT legacy), with the ledger accounts `INPUT_IGST_AC`, `INPUT_CGST_AC`, `INPUT_SGST_AC`,
`OUTPUT_IGST_AC`, `OUTPUT_CGST_AC`, `OUTPUT_SGST_AC`.

**Tax code POJO** (`in.android.vyapar.BizLogic.TaxCode` wraps `sen`):
`TaxCode(taxCodeId, taxCodeName, taxRate, taxCodeType, taxRateType, taxCodesMap: HashMap<Integer,Double>)`
(Parcelable marshals exactly these 5 + the map). `taxCodesMap` is what makes a *tax group* work: group id → child rate.
`TaxCode.e(int,int,String,String)` (create/update) and `TaxCode.f(int,List,String)` (save group) return an enum of results:
`ERROR_TAX_CODE_NAME_EMPTY, ERROR_TAX_CODE_RATE_EMPTY, ERROR_TAX_CODE_WITH_SAME_NAME_EXIST, ERROR_COA_SYSTEM_ACCOUNT,
ERROR_TAX_CODE_NOT_USED, ERROR_TAX_CODE_UPDATED_SUCCESS, ERROR_TAX_MAPPING_SAVED_SUCCESS` — i.e. delete/rename are guarded
by "is this tax code referenced anywhere" (`select item_tax_id from kb_items … union all select txn_tax_id … union all
select lineitem_tax_id …`).
`in.android.vyapar.TaxActivity` is a 2-tab master: `tax_rates` + `tax_groups` (`v0_4.p(v1_2, …vbj.tax_rates)` / `…vbj.tax_groups`).

**Naming convention (the real slab model)**: rates are *not* a fixed enum; they are `kb_tax_code` rows named
`<HEAD>@<RATE>%` with head ∈ {IGST, CGST, SGST, GST, VAT}. Observed literals in migration/validation SQL:
`IGST@0.25%`, `CGST@0.125%`, `SGST@0.125%`, `IGST@3%`, `CGST@1.5%`, `SGST@1.5%`, `GST@3%`, `GST@0%`, `GST@28%`,
`CGST@14%`, `SGST@14%`, `IGST@40%`, `CGST@20%`, `SGST@20%`, `GST@40%`, `VAT 0%`, `VAT@0%`, `VAT@5%`, `VAT 13%`, `exempt`.
(`select * from kb_tax_code where lower(tax_code_name) = 'exempt'` / `'vat 0%'` … ;
`where lower(trim(tax_code_name)) in (lower('IGST@40%'), lower('SGST@20%'), lower('CGST@20%'), lower('GST@40%'))`.)
So a clone needs: a mutable tax-code table keyed by name, seeded IGST/CGST+SGST pairs, plus special codes Exempt / Nil /
Not-applicable, and CESS (+ "Additional CESS per unit") as separate heads.

**Tax modes / switches** (`kb_settings` key-value, `setting_key = 'VYAPAR.GSTENABLED'` is the master):
`SETTING_GST_ENABLED`, `SETTING_TAX_ENABLED`, `SETTING_TAXINVOICE_ENABLED`, `SETTING_ITEMWISE_TAX_ENABLED`,
`SETTING_ENABLE_INCLUSIVE_TAX_ON_TXN` ("Inclusive Tax on transcation"), `inclusive_tax_on_transaction_setting`,
`SETTING_ITEM_RATE_TAX_ON_OUTWARD_TXN_INCLUSIVE`, `SETTING_ITEM_RATE_TAX_ON_INWARD_TXN_INCLUSIVE`,
`igst_on_intra` (charge IGST even on intra-state), `isUnionTerritoryForUTGST`,
`SETTING_TAX_DEDUCTED_AT_SOURCE_ENABLED`, `SETTING_TAX_COLLECTED_AT_SOURCE_ENABLED`,
`SETTING_IS_ROUND_OFF_ENABLED`, `SETTING_ROUND_OFF_TYPE`, `SETTING_ROUND_OFF_UPTO`,
`consider_non_tax_transaction_as_exempted` / `…as_nil_rated`, `dont_consider_non_tax_transactions`,
`gstr_9_consider_non_tax_as_exempted`, `SETTING_PRINT_BILL_OF_SUPPLY_FOR_NON_TAX_TXN`, `SETTING_PRINT_TAX_DETAILS`,
`VYAPAR.HSNCODECOLUMNHEADERVALUE`, `SETTING_HSN_SAC_ENABLED`, `VYAPAR.HSNSACENABLED`,
`VYAPAR.TXN_WISE_SALE_RETURN_TAX_ID`, `do_you_have_ecommerce_gstin`, `e_commerce_gstin_gstr`.
Item-level tax type spinner values observed as labels: `Incl.`, `Excl.`, `Override`, `Not Applicable`
(default `item_tax_type = 2`; SQL treats `item_tax_type = 1` as the odd case — mapping is **inferred**, not proven).

Entry point: `settings.activities.TaxesAndGstSettingsActivity` is a *router* (jadx/droidasc: it checks
`bka.a(j9k.TAXES_AND_GST_SETTINGS, "action_view")` for permission, then either launches `in.android.vyapar.TaxActivity`
or hosts a search-result fragment `nhn`/`mhn` with `searched_view_id`), i.e. the "Taxes & GST" settings row is itself a
searchable settings pane, not a form.

Bulk rate change: `bulkUpdateItems.BulkUpdateTaxSlabActivity` (`BULK_UPDATE_TAX_SLAB`, `bulk_update_tax_slab`,
`you_are_about_to_update_tax_slab_of`, `bulk_update_tax_slab_attention_option_for_batch_mrp`,
`*Bulk update tax slabs failed, statusCode: ` → server call) — a GST-rate-revision utility that rewrites many items' slabs.

---

## 4. GST return reports — what each computes

**GSTR-1** UI model `Loc9;` (`GSTR1ReportUiModel`) exposes exactly these aggregates — this is the spec of what GSTR-1 must
compute (from its `toString`, classes6.dex):
`gstR1ReportModelList`, `reportObjectListToBeShownForSaleReturn`, `reportObjectListToBeShownForSale`, `gstr1Flags`,
`totalInvoiceValue`, `totalTaxableValue`, `totalIGSTValue`, `totalSGSTValue`, `totalCGSTValue`, `totalCESSValue`,
`totalStateSpecificCESSValue`, `totalOtherValue`, `totalAdditionalCESSValue`, and the same 9 again `…ForSaleReturn`.
Row model `Ljc9;` (`GSTR1ReportModel`) = `transactionId, transactionType, nameId, firmId, taxRateId, transactionDescription,
gstinNo, invoiceNo, invoicePrefix, invoiceNumber, invoiceDate, invoiceValue, invoiceTaxableValue, rate, cessRate,
stateSpecificCESSRate, IGSTAmt, SGSTAmt, CGSTAmt, CESSAmt, stateSpecificCESSAmount, otherAmt, additionalCESSAmt,
placeOfSupply, isEntryIncorrect, isReverseChargeApplicable, returnDate, returnRefNo`.
Sub-reports (resource strings): `GSTR_1_Sales_Report`, `GSTR_1_CDN_Report` (credit/debit notes), buttons
`GSTR1 Sale Generate` / `GSTR1 CDN Generate`, filter `…cb_exclude_e_invoice_generated_transactions`.
JSON export model `Luga;` = `Gstr1JsonObjectModel(gstin, returnPeriod, b2b, b2cl, b2cs, nil, hsn, cdnr, cdnur, docIssue,
version, hash)` — i.e. the GSTN GSTR-1 JSON skeleton (B2B / B2CL / B2CS / Nil / HSN / CDNR / CDNUR / document-issue),
**built client-side**. `B2b(ctin=` confirms the GSTN field naming.
Warning UX: `GSTR1Flags(showOther=…)`, `showGSTR1Warning`, `view_gstr1_report_warning_dialog`.

**GSTR-2** = purchase side: `GSTR2 Purchase Generate`, `GSTR2 CDN Generate`, columns
`report_gstr2_col_gstin|_igst|_cgst|_sgst|_cess_rate`, `report_gstr2_group_invoice`, `report_gstr2_reverse_charge`,
`report_gstr2_section_purchase_return`, `report_gstr2_jw_txn_error` (job-work excluded), warning copy
(`report_gstr2_warning_title/_red_line/_orange_box/_dont_show_again`).

**GSTR-3B** = summary; computed by the big UNION-ALL aggregate that groups by reverse-charge / ITC / voucher type and adds
line-item tax + additional cess + the three additional-charge tax buckets:
`… group by txn_reverse_charge,txn_itc_applicable,txn_type union all select txn_type,txn_reverse_charge,0 as tcs_sum_amount,
0 as tds_sum_amount, sum(ifnull(lineitem_tax_amount,0)+ifnull(lineitem_additional_cess,0)) … where txn_type in (1,60,2,71,61,23,21,7)`
(×4 more legs for `ac1_tax_amount`, `ac2_tax_amount`, `ac3_tax_amount`) + separate tcs/tds sums.
Its HTML template contains the official 3B/9 row labels (`ITC as per GSTR-2B (Table 3(I) thereof)`,
`IGST paid on import of goods (including supplies from SEZ)`, `Total ITC to be lapsed in current financial year (E+F+J)`,
`Supplies / tax (outward) declared through amendments (+)`, column set `Turnover | Central Tax | State/UT Tax |
Integrated Tax | Cess | Sheet Validation Error(s) | GST portal validation error(s)`).
`Summary … IGST Amount | CGST Amount | SGST Amount | CESS Amount | Total Taxable Value`.

**GSTR-4** = composition-scheme annual/quarterly return (`GSTR_4_Report`, `Total Composition taxable person`,
`Composition taxable person`, `Supplies made to Composition Taxable Persons`,
`Credit reversed on opting in the composition scheme (-)`, `Credit availed on opting out of the composition scheme (+)`,
`From a supplier under composition scheme, Exempt and Nil rated supply`).

**GSTR-9 / GSTR-9A** = annual return (regular / auditor) at `activities.report.GSTR9ReportActivity`,
`GSTR9AReportActivity`; share filter row `gstr_9_consider_non_tax_as_exempted`.

**GSTRTxnReportActivity** = `GSTR_DETAIL_REPORT` (type 64) — transaction-wise GSTR data,
`GSTR1TxnReportGroupingKey(txnId=`, `GSTR1DocsModel(txnType=`.

Core tax aggregate SQL actually present in the pool (Tax Report / GST Rate Report):

```
SELECT txn_type, tax_id, SUM(taxable_value) AS taxable_value, SUM(tax_amount) AS tax_amount FROM (
Select Txns.txn_name_id, Txns.txn_type, sum(LI.lineitem_tax_amount) AS lineitem_tax_amount,
  sum(LI.lineitem_additional_cess) AS lineitem_additional_cess
  from kb_lineitems LI inner join kb_transactions Txns on LI.lineitem_txn_id = Txns.txn_id
  where Txns.txn_type in (1 ,2, 60 ,61, 7, 21, 23) [AND txn_date <= / >=]
Select LI.lineitem_tax_id, Txns.txn_type, TOTAL(LI.lineitem_tax_amount) AS lineitem_tax_amount,
  TOTAL(LI.quantity*priceperunit-ifnull(lineitem_discount_amount,0)) as taxable_value
  ... AND LI.lineitem_tax_id > 0
select txn_tax_id, txn_type , TOTAL(txn_tax_amount) AS txn_tax_amount , TOTAL(taxable_val) as taxable_value from (
  select Txn.txn_id, Txn.txn_tax_id, Txn.txn_type, Txn.txn_tax_amount,
    (total(total_amount) - ifnull(Txn.txn_discount_amount, 0)) AS taxable_val …)
```
Taxable value = `qty*unit_price − lineitem_discount − txn_discount_amount`; the head split (IGST vs CGST+SGST) is resolved
from place-of-supply/party state, with `igst_on_intra` overriding. Voucher types treated as tax-bearing:
`1, 2, 60, 61, 7, 21, 23, 71` (sale, purchase, sale-return, purchase-return, expense, and two others — exact ids are the
schema dimension's job).

HSN/SAC aggregate:
```
SELECT I.item_name, I.item_hsn_sac_code, L.quantity, L.total_amount, L.lineitem_tax_id, L.lineitem_tax_amount,
       L.lineitem_additional_cess, T.txn_type, T.txn_discount_percent, T.txn_tax_id
  FROM ((kb_lineitems AS L INNER JOIN kb_transactions AS T ON L.lineitem_txn_id = T.txn_id)
        INNER JOIN kb_items AS I ON L.item_id = I.item_id) WHERE T.txn_date BETWEEN …
```

---

## 5. RCM (reverse charge) and ITC

* Column level: `txn_reverse_charge integer default 0` and `txn_itc_applicable integer default 0`,
  `lineitem_itc_applicable integer default 0`, `ac{1,2,3}_itc_applicable integer default 0`.
* Reads: `ifnull(txn_reverse_charge, 0) as reverseCharge`, `lineitem_itc_applicable as itcApplicable`; GSTR grouping
  `group by txnId, taxId, itcApplicable`.
* Model field `isReverseChargeApplicable` in every GSTR-1 row; GSTR-2 column `report_gstr2_reverse_charge`.
* UI copy: `Reverse Charge`, `Reverse Charge Tax`, `Reverse Charge Transactions`, `Reverse Charge Affected parties`.
* Opening-balance rows carry an ITC ledger: `IGST_Credit_available`, `CGST_Credit_available`, `SGST_Credit_available`,
  `CESS_Credit_available`, `Add_Cess`, `ITC as per GSTR-2B`.
* Tax-group aggregate keys on it: `select tax_code_id from kb_tax_code where tax_code_type = 0 and tax_rate_type in (3,2,1,5,6)
  and tax_code_id not in (select tax_mapping_code_id from kb_tax_mapping)` (un-grouped GST rates feed the group picker).

## 6. TDS / TCS

Own classes: `tds.{ManageTdsActivity,AddOrEditTdsActivity}`, `tcs.{ManageTcsActivity,TcsActivity}`, `tcs.reports.TcsReport`,
`reports.tds.ui.TdsReportActivity`; operations enum `vyapar.shared.domain.constants.{TdsOperationResult,TcsOperationResult}`.
DB: `tds_tax_rates(name, percentage, section_id)` with `txn_tds_tax_id/txn_tds_tax_amount`;
`kb_tcs_tax_rates(tcs_tax_name, tcs_tax_percentage, tcs_tax_nature_of_collection_id)` with `txn_tcs_tax_id/txn_tcs_tax_amount`.
Reports are **payable vs receivable** pairs (`TDS_PAYABLE_REPORT` type 68 / `TDS_RECEIVABLE_REPORT` 69 driven by extras;
`TCS_27EQ_REPORT` 58 = Form 27EQ / `form27eq_report`, `TCS_RECEIVABLE_REPORT` 57), same Activity, direction from voucher type.

**Nature-of-payment master list is compiled into the app, not the DB** — two Kotlin enums seeded with statutory defaults:

* TDS `Lbln;` (57 values) = `(sectionId, sectionName, sectionNumber, percentage:double, returnCode:String, isDeprecated)`.
  Indian set: `192`(1.0) `192A`(10.0) `193`(10.0) `194`(10.0) `194A`(10.0) `194B` "Lottery winnings"(30.0)
  `194BB`(30.0) `194C(a)` "Payment to contractors for HUF/Individuals"(1.0) `194C(b)`(2.0) `194D`(5.0) `194DA`(5.0)
  `194EE`(10.0) `194F`(20.0) `194G`(5.0) `194H` "Commission or brokerage"(5.0) `194-I(a)`(2.0) `194-I(b)`(10.0)
  `194-IA`(1.0) `194-IB`(5.0) `194-IC`(10.0) `194J(i)`(2.0) `194J(iii)`(10.0) `194K`(10.0) `194LA`(10.0)
  `194-O`(1.0) `194Q` "TDS on sales"(0.1) `194R`(10.0) `194S` "Transfer of Virtual Digital Asset"(1.0) + `Others`.
  Second set `SECTION_RC_10xx` (28 values) carries `returnCode` "1002"…"1037" and non-Indian section strings
  (`392`, `393(1)[Sl.6(i).D(a)]`, `394(1)[Sl.8.D(a)]`) with the same descriptions → a **second-jurisdiction withholding
  code table** (the app ships India/UAE/Kenya/Tanzania/Nigeria/Philippines/Canada country strings and
  `currentLicenseMaxCountryCodeAllowed`). Which country is labelled **unverified**.
* TCS `Lyin;` (13 values): `206C(1H)` "Sale of goods"(0), then RC codes — `394(1)[Sl.4]` "Scrap sale"(2.0),
  `394(1)[Sl.6.D(a)]` "Motor vehicle sale"(1.0), `Sl.6.D(b)` "Wristwatch sale"/"Art piece sale"/"Sunglasses sale"/
  "Bag sale"/"Shoes sale"/"Sportswear & equipment sale"(1.0 each), `394(1)[Sl.1]` "Liquor Sale"(2.0),
  `394(1)[Sl.8.D(a)]` "Overseas Tour Pkg (<=10L)"(2.0), `394(1)[Sl.8.D(b)]` "Overseas Tour Pkg (>10L)"(2.0), `Others`.
* Deprecated markers exist (`tv_tds_deprecated_section_warning`, `tv_tcs_deprecated_section_warning`, `isDeprecated`),
  plus CRUD guards `cannot_delete_tds_header/desc`, `cannot_edit_tcs_*`, `add_tds_rate`, `empty_search_tds_rate_mssg`,
  `tds_section_empty_error_mssg`.

## 7. HSN / SAC

* Two distinct reports: `HSN_SAC_REPORT` (item master → HSN/SAC per item; `HsnOrSacReportActivity`, `HsnOrSacReportDbModel(
  itemName, HsnOrSac, txnType, quantity, totalValue, taxId, taxAmount, additionalCess, txnDiscPerc, txnTaxId)`,
  `HsnOrSacReportUiModel(hsnOrSac=`, `HsnOrSacReportUiModelGroupByKey(hsnOrSac=`) and
  `SALE_SUMMARY_BY_HSN_REPORT` (transaction-wise; `SummaryByHsnReportActivity`).
* `in.android.vyapar.HSNLookUpActivity` is a **WebView lookup**: JS enabled, `addJavascriptInterface(JavaScriptInterface,
  "AndroidJSInterface")`, offline group `grpNoInternet`, no hard-coded HSN master URL in the dex pool (searched
  `hsn_?lookup|lookup_url` → only class/name symbols) → the HSN/SAC **code database is remote**, the picker is server-backed.
  Local storage is only the free-text column `kb_items.item_hsn_sac_code varchar(32)`.
* Settings/print toggles: `SETTING_HSN_SAC_ENABLED`, `VYAPAR.HSNSACENABLED`, `Print_regular_item_table_setting_hsn/sac`,
  `card_view_item_hsn_code_text/value`, `card_view_export_item_hsn_code`, `EnableHSNSACcode_what`, `add_sac`,
  `add_sac_and_a_default_gst_rate`, `hsn_sac_code`. SAC also attaches to *additional charges*: `ac1SacCode`, `ac2SacCode`,
  `ac3SacCode` (extra-charge lines carry their own SAC + tax, and are folded into GSTR-3B).
* GSTR-1 JSON includes the `hsn` section; GSTR-9/9A HTML has `Summary For B2B, SEZ, DE (4A, 4B, 6B, 6C)`.

## 8. Composition vs regular

Present as a firm/party attribute rather than a mode switch: `Total Composition taxable person`, `Composition taxable person`,
`to Composition`, `do_you_have_ecommerce_gstin`, `Supplies made to Unregistered Persons / Composition Taxable Persons /
UIN holders` (GSTR-1 B2CS template), `gstTypeHeading/gstTypeValue/gstTypeGroup`, `PARTY_GST_TYPE`,
`INTRAB2B` / `INTRAB2C` (and `INTER…` counterparts), `e_commerce_gstin_gstr`. GSTR-4 is the composition return;
GSTR-9A is the audit/composition-scheme annual variant. The GST-enable blurb in resources states the precondition explicitly:
"Businesses which have GSTIN no. & are registered under **regular or composite scheme** can enable GST."
(The scheme is therefore an enum on firm + party; the exact storage column is left to the schema dimension.)

## 9. E-invoice (IRN) and e-way bill — client vs server split

**Everything that talks to GSTN/NIC/GSP is server-mediated through `api*.vyaparapp.in`.** No NIC/GSP credentials or signing
material is in the APK; the app only holds GSP *username/password session* handoffs.

Endpoints (single constant class `Lhm0;`, base `https://api1.vyaparapp.in` / `api2`):
```
api/ns/gsp/einvoice            api/ns/gsp/einvoice/generate   api/ns/gsp/einvoice/login   /api/ns/gsp/einvoice/print
api/ns/gsp/eway                api/ns/gsp/eway/generate       api/ns/gsp/eway/login       api/ns/gsp/eway/verify
/api/ns/gsp/eway/print         api/ns/ewaybill/valid[/{gstin}]
api/ns/gsp/otp/send  api/ns/gsp/otp/verify  api/ns/gsp/signup  api/ns/gsp/gst
api/ns/ib-einvoice/AE/signin   /api/ns/ib-einvoice/AE/invoices/{txnType}/{uuid}/download   (UAE e-invoicing, 3rd-party)
```
Client-side responsibilities observed:
* **Owns state**: `EInvoiceUiModel(…, txnIrnNumber=, txnEInvoiceQr=, txnCancelledEInvoiceDate=, einvoicePdf=,
  isActiveEInvoice=, isGenerateEInvoiceBtnEnabled=, showGenerateEInvoiceOption=)`;
  `EWayBillUiState(isLoading=…, eWayBillNumber=, ewayBillDate=, generateEwayBill=, txnEwayBillApiGenerated=,
  eWaybillDetails=(transporterId, transporterName, transporterDocNo, transporterDocDate, vehicleNo, vehicleType))`;
  persisted to `kb_transactions` (`txn_irn_number`, `txn_einvoice_qr`) and `transaction_attributes`
  (`einvoice_id`, `einvoice_status integer not null default 0`, `einvoice_metadata text`, `cancelled_einvoice_date`,
  `txn_einvoice_qr`).
* **Validates before asking the server**: `ERROR_GENERATE_EINVOICE_INV_NO_EMPTY`, `ERROR_GENERATE_EINVOICE_INVALID_PARTY`,
  `ERROR_GENERATE_EINVOICE_INVALID_TXNTYPE`, `ERROR_GENERATE_EINVOICE_EMPTY_PLACE_OF_SUPPLY`,
  `ERROR_GENERATE_EWAYBILL_INVALID_DATE/INV_NO_EMPTY/INVALID_PARTY/INVALID_TXNTYPE/INVALID_INV_NO_LENGTH/
  EMPTY_PLACE_OF_SUPPLY/COMPOSITE_USER_INVALID_AMOUNT`, `ERROR_CANNOT_MODIFY_TXN_EWAY_BILL_GENERATED`,
  `error_transaction_cant_cancel_as_ewaybill_generated`, `e_invoice_place_of_supply_warning`,
  `exempt_non_gst_e_invoice_warning`, `ewb_error_no_taxable_value`, `ewb_error_regular_gstin_mismatch`,
  `ewb_error_dispatch_gstin_mismatch`, `e_way_bill_min_tax_error`. Queries exclude already-e-invoiced rows:
  `AND NOT (NULLIF(TRIM(txn_irn_number), '') IS NOT NULL AND cancelled_einvoice_date IS NULL)`.
* **Renders**: QR is drawn locally (zxing `Can only encode QR_CODE, but got`) from the server-supplied
  `signedQRCode` / `txn_einvoice_qr` string; PDF comes back as `einvoicePdf=` and via `/api/ns/gsp/einvoice/print`.
* **GSP credential handoff (OTP) is in-app**: `GSPRegisterViaEInvoiceRequest(username=`,
  `GSPLoginWithCredentialRequest(username=`, `GSPRegisterViaEWayVerifyOTPRequest(otp=`,
  `ShowGSPLoginBottomSheetForViewEInvoice|CancelEInvoice|ViewEWB|CancelEWB`,
  `gsp_register_credentials_einvoice_body_text`, `RegisterViaEWaybill`, `RegisterVerifyEWaybillOTP`,
  `Update TxnEInvoice Cancelled Date`, `EInvoiceCancelSuccess(cancelDate=`, `EWayBillCancelSuccess`,
  `GenerateEInvoiceWithPlaceOfSupplyCheck` / `WithoutPlaceOfSupplyCheck`, `UpdateEwayBillMode(ewayBillMode=`.
  Payload DTOs are Kotlin/Compose-side (`vyapar.shared.data.models.einvoice.EInvoiceDTO`,
  `vyapar.shared.data.models.ewaybill.EWayBillDTO(supplyType=…, EWayLineItemDTO)`,
  `GEWayBillRequest(supplyType=outward, subSupplyType=Supply, documentType=…)`), i.e. the NIC payload shape is modelled in
  the shared module and posted to Vyapar's backend.
* **Deep-link into the government portals is WebView-only**: `https://einvoice1.gst.gov.in/` and
  `https://ewaybillgst.gov.in/` appear exactly once each, as Koin module strings (`Lr6j/p6j…` lambda `Lf69;->invoke`
  case 3/4 next to YouTube tutorial links) behind resource rows `register_on_einvoice_portal`,
  `register_on_ewaybill_portal`, `register_via_einvoice_text`, `register_via_ewaybill_text`,
  `verify_and_generate_einvoice`, `verify_and_generate_ewaybill`.
* UI shells `einvoice.EInvoiceActivity` and `ewaybill.EWayBillActivity` are registered in the manifest; listeners
  `* must implement IRNMenuInteractionListener` / `EWBMenuInteractionListener`, menu items `ViewIRNMenu`,
  `CancelIRNMenu`, `generate_ewaybill`, `actv_ewaybill`, `deeplink_ewaybill`, `ewaybill_session_expired`.
* `GSPLoginValue`, `GstinData`, `GSPLoginSendOTPResponse`, `GSPLoginVerifyOTPResponse`, `GspMappedUserListResponse`,
  `UaeEInvoiceSignInResponse(nextStep=…)` → session/OTP state also modelled client-side but resolved server-side
  (`#isValidGSTIN: Response data is null`, `* token is null`, `API_KEY_IS_VALID_GSTIN from GSTIN: `).
* Manifest `<queries>` lists `in.cleartax.clearone`, `com.zoho.invoice`,
  `com.asap.codenicely.pdf.gstinvoicing…quickinvoice.gstinvoicing` — **visibility for share/export**, not a filing
  integration (they sit among `com.imo…`, `com.koo.app`, calculator apps).

## 10. Clone implications (scope of reports-tax)

1. Ship the registry as data, not code: one table/enum with
   `(key, titleRes, reportTypeId, activity/route, launchExtras, pricingResource, isHeading)` reproduces the whole 60-report
   catalogue and its premium gating with ~60 rows; 12 Activities are reused across 60 reports via extras.
2. The GST engine reduces to: tax-code table keyed by `<HEAD>@<RATE>%` + tax groups (`kb_tax_mapping`) + per-item/per-line/
   per-voucher tax id and incl/excl flag + reverse-charge/ITC flags + additional-charge (3 slots) tax + round-off; then
   GSTR-1/2/3B/4/9/9A are *aggregations* of `kb_lineitems ⋈ kb_transactions` grouped by
   (gstin, tax_id, cess, place_of_supply, txn_type, reverse_charge, itc_applicable).
3. TDS/TCS nature masters belong in app code (with default % and deprecated flags) — they never change per-tenant.
4. IRN/e-way-bill needs a backend. For a clone, keep the client half (UI state machine, pre-validation codes, local
   columns, QR rendering, PDF display, OTP bottom sheet) and treat GSTN connectivity as a pluggable server API with the
   same 14 endpoint shapes.
5. Report rendering contract: HTML template → WebView/PDF, POI HSSF → .xls, and (GSTR only) GSTN-JSON download.
6. `AutoSyncBaseReportActivity` implies every report starts with a company sync — worth keeping as an explicit
   "ensure-synced" step so numbers reconcile with the desktop/web product.
