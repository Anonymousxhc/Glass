# Vyapar — Business SQLite Schema (dimension: db-schema)

Target: `in.android.vyapar` v31.0.1 (vc728). Evidence source: full MUTF-8 string pools of `dex/raw/classes*.dex` (parsed programmatically, not `strings`-fragments), `droidasc` class dumps of the obfuscated schema-holder classes, `dart_out/*.jsonl`.
Counts: **224 CREATE TABLE strings** total = **163 business** (84 unique tables) + **59 SDK-owned** + 2 generic fragments. **195 ALTER TABLE strings** = **122 business** (15 tables) + 73 SDK.

---

## 1. Storage architecture (observed/inferred)

- **Two-tier database layout.** A *master* database holds the company registry; **each company gets its own SQLite file**.
  - Master DB file name constant: `vypmaster` (classes6.dex string 0x96ff0a; opened in `Lf4m;-><init>` via helper `k09(context,"vypmaster",…)` — droidasc dump).
  - `kb_companies.company_db_name varchar(20)` stores the per-company file stem; queries `select * from kb_companies where company_db_name = ?`, `select company_id from kb_companies where company_db_name = ?` (classes6.dex).
  - Company file name builder `Lgup;->a(String)` (droidasc): lower-case, strip `[^a-zA-Z0-9._ -]`, cap 40 chars, append 13-digit epoch if name collides/ends with 13 digits, then `.concat(".vyp")`. **Company DBs are `*.vyp` files.** (The stray Dart `.db` string is the sqflite suffix used by the Flutter `cacheObject` HTTP-cache table, not the business DB.)
  - `Ldj5;` = schema-holder class for `ops_logs`; `Lme4;` for `kb_companies`; `Lddg;` for `kb_names` — pattern: each business table has a `k4m` subclass whose `<clinit>`/`<init>` exposes `a()`=PK column, `b()`=CREATE SQL, `c()`=table name. Clone-relevant: registry-style schema management.
- **Per-table SQL is embedded as one const string per table** (mostly in classes6.dex). Some long statements are Kotlin-concatenated from shared fragments (FK tails like `integer default null references kb_tax_code(tax_code_id)` appear as separate pool strings ~0x92f8xx–0x92fbc2).
- **Settings** are a key/value table `kb_settings(setting_key UNIQUE, setting_value)` with hundreds of `VYAPAR.*` keys (e.g. `VYAPAR.BARCODESCANNINGENABLED`, `VYAPAR.CUSTOMNAMEFORSALE`, `VYAPAR.DEFAULTCOMPANY`) — UI/toggle config is NOT columned out; only transactional entities are tables.
- **Indexes (business, only 2 found):** `create index if not exists index_transaction_txn_type on kb_transactions (txn_type)` (0x83404a); `create unique index unique_key_index on kb_settings(setting_key)` (0x91a17f).
- Dart/KMP layers reuse the same DBs (method channel `AndroidTransactionHelper.transact_26b9a8`); Dart's only own DDL is plugin `cacheObject` table (HTTP cache). No SQLDelight; Room used only by SDK internals.

---

## 2. Table catalog — 84 business tables by domain

Master DB (inferred from device/company-registry semantics): `kb_companies`, `ops_logs`, `feature_usage`, `kb_log`, `kb_app_inbox_msg`. Everything else is per-company DB (single schema per company). Also `urp_*` (users/permissions), `audit_trails`, `leads`, `tasks`, `task_attachments`, `stores`, `transaction_inbox` and all inventory/txn tables live in each company DB.

| # | table | purpose |
|---|-------|---------|
| 1 | kb_companies | company registry (master): name, DB file, backup/sync flags, access state |
| 2 | kb_firms | firm/branch identity + document numbering counters + GSTIN/state/bank — one row per firm inside a company DB |
| 3 | kb_settings | global KV settings (unique key) |
| 4 | kb_names | **parties** (customers+suppliers+expense/pay income accounts) |
| 5 | kb_address | extra per-party addresses (FK kb_names), 2000-char address + line_1/2/city/pincode/state/country |
| 6 | kb_party_groups | party group tree labels (name UNIQUE) |
| 7 | kb_party_item_rate | per-party item price overrides (UNIQUE(item,party) ON CONFLICT REPLACE) |
| 8 | party_notes | free-text notes per party |
| 9 | party_to_party_transfer | party↔party balance transfer (links paid/received txns) |
| 10 | party_item_service_reminder | service-reminder per (party,item): period, last service, last reminder |
| 11 | kb_payment_terms | payment terms (term_name/term_days UNIQUE, is_default) |
| 12 | kb_items | items/services/assemblies (incl. tax, HSN/SAC, MRP, wholesale, catalogue, service-reminder fields) |
| 13 | kb_item_categories | category names UNIQUE |
| 14 | kb_item_categories_mapping | item↔category M:N |
| 15 | kb_item_units | units (name, short_name, editable/deletable flags) |
| 16 | kb_item_units_mapping | composite units: base↔secondary + conversion_rate |
| 17 | kb_item_stock_tracking | **batch/serial/expiry carrier** (ist_ = item stock tracking) |
| 18 | kb_serial_details | per-item serial numbers + qty |
| 19 | kb_serial_mapping | serial ↔ lineitem/adjustment usage |
| 20 | kb_transactions | **every voucher** (sale/purchase/payin/payout/cash/debit/credit/journal/ewaybill/e-invoice fields) |
| 21 | kb_lineitems | voucher lines (item, qty, price, tax, discount, batch/serial/expiry, ITC, additional costs) |
| 22 | kb_txn_links | payment↔invoice allocations (txn1, txn2, amount) |
| 23 | kb_linked_transactions | generic txn↔txn link (source,destination,date) |
| 24 | kb_closed_link_txn_table | closed-link record (amount,type,date,ref) for cheque/links |
| 25 | kb_cheque_status | cheque lifecycle (post-dated/on-hold) with status + close ref |
| 26 | kb_paymentTypes | payment modes: Cash/Bank/Card/UPI/Wallet + opening balance + bank details |
| 27 | kb_bank_accounts | bank accounts keyed to payment type (ref/number/type) |
| 28 | kb_bank_adjustments | bank→bank transfer/adjustment |
| 29 | kb_cash_adjustments | cash overdraft/excess |
| 30 | txn_payment_mapping | split-payment rows per txn (account, cheque ref, EDC fields, amount) |
| 31 | kb_tax_code | GST/other tax slabs (name, rate, type=GST/…, rate_type per cent/per unit) |
| 32 | kb_tax_mapping | tax-group → child tax rows |
| 33 | kb_tcs_tax_rates | TCS rates + nature-of-collection |
| 34 | tds_tax_rates | TDS section rates |
| 35 | kb_extra_charges | extra charges (freight etc.) with SAC + tax |
| 36 | kb_prefix | **invoice number series per txn type** (firm,txn_type,prefix_value,is_default) |
| 37 | attachments | file store (url,name) generic |
| 38 | transaction_attachments | txn↔attachment (uuid unique) |
| 39 | transaction_attributes | e-invoice IRN metadata per txn (einvoice_id/status/metadata JSON) |
| 40 | kb_images | image BLOB store (logo/signature/visiting card) |
| 41 | kb_item_images | item image blobs (+catalogue sync flags) |
| 42 | item_adj_attachment_mappings | item-adjustment↔attachment |
| 43 | item_adj_image_mappings | item-adjustment↔image |
| 44 | kb_item_adjustments | stock adjustments (increase/decrease/mfg), batch-aware, self-FK for mfg |
| 45 | kb_adjustment_ist_mapping | adjustment↔batch qty split |
| 46 | item_def_assembly | BOM for definition-based assembly items |
| 47 | item_def_assembly_additional_costs | additional costs per assembly item (AC 1–5) |
| 48 | item_mfg_assembly_additional_costs | AC costs per manufacture adjustment (payment + txn refs) |
| 49 | kb_catalogue_items | catalogue (webstore) item snapshot + code/price/category |
| 50 | stores | multi-location: godowns/outlets (type,name UNIQUE, GSTIN,address) |
| 51 | store_transactions | stock transfer between stores (from/to, sub_type) |
| 52 | store_line_items | transferred items/qty rows |
| 53 | loyalty_txn | loyalty points rewarded/redeemed per txn/party(mobile) |
| 54 | repeat_invoice_template | recurring-invoice template (schedule fields + txn_json) |
| 55 | loan_accounts | loan/asset accounts (lender, opening bal, interest, term) |
| 56 | loan_transactions | EMI/disbursement rows (principal/interest/payment acc) |
| 57 | currencies | custom currencies (symbol, uid) |
| 58 | currency_exchange_rate | rate rows per currency (update_type) |
| 59 | terms_and_conditions | T&C blocks (header, description, is_active) |
| 60 | tnc_txn_type_mapping | T&C per voucher type, is_default |
| 61 | kb_udf_fields | user-defined fields metadata (type, data format, txn type, print-on-invoice) |
| 62 | kb_udf_values | UDF values per entity ref id |
| 63 | kb_custom_fields | legacy custom field defs (display name, type, visibility) |
| 64 | kb_sms | SMS outbox log per company/txn |
| 65 | kb_txn_message_config | message-template fields per txn type (key-value rows) |
| 66 | kb_log | local log rows (reason/details) |
| 67 | kb_app_inbox_msg | app-store review/inbox message state |
| 68 | ops_logs | device operation log (master): company switches, app/db version |
| 69 | feature_usage | per-company per-feature monthly access counters (master) |
| 70 | audit_trails | per-txn change log (user, device, version, change_logs JSON) |
| 71 | recycle_bin | deleted-voucher JSON snapshots (firm,type,date,status) |
| 72 | transaction_inbox | incoming transactions from account aggregation (party,amount,url) |
| 73 | kb_payment_gateway | Razorpay/KCA KYC entity record (gstin, PAN, docs, status, UUIDs) |
| 74 | pg_txn_uti_mapping | Vyapar-pay UTR/vyapar-unique-txn-id per payment txn |
| 75 | leads | sales-lead records (name UNIQUE → converted party_id) |
| 76 | tasks | follow-up tasks per lead (type,status,assignee,date,geo) |
| 77 | task_attachments | task file paths (remote_path) |
| 78 | urp_users | multi-user/staff accounts (role id, passcode, permissions JSON text) |
| 79 | urp_activity | user activity feed (actor,resource,operation) |
| 80 | urp_item_category_access | per-user category visibility whitelist |
| 81 | chart_of_accounts_mapping | **V2 accounts**: hierarchical COA map (parent,account_code UNIQUE,foreign ref) |
| 82 | journal_entry | **V2** journal header (firm,reference_number,date,audit cols) |
| 83 | journal_entry_line_items | **V2** journal lines (account_id, amount_type, txn_link) |
| 84 | other_accounts | **V2** misc accounts (opening bal, type, identifier) |

---

## 3. Core tables — full extracted DDL (longest variants, classes6.dex)

### 3.1 kb_companies — company registry, master DB (0x84c3e5; tail confirmed from `Lddg`-style clinit of `Lme4;`)
```sql
create table kb_companies(
    company_id integer primary key autoincrement,
    company_name varchar(128) not null,
    company_db_name varchar(20) not null,
    comp_date_created datetime default (datetime('now','localtime')),
    comp_date_modified datetime default (datetime('now','localtime')),
    comp_auto_backup_status varchar(1) default 0,
    comp_last_auto_backup_time datetime,
    comp_auto_backup_duration varchar(2) default 2,
    comp_last_backup_time datetime default null,
    sync_enabled varchar(1) default 0,
    sync_company_global_id varchar(256) default null,
    initial_company_id varchar(256) default null,
    current_company_id varchar(256) default null,
    company_last_accessed_time datetime default (datetime('now','localtime')),
    shared_to_userid varchar(128) default null,
    company_access_status integer default 0,          -- enum Unlocked (me4 uses m44.Unlocked.getValue())
    last_activity_at datetime default null,
    company_created_date datetime default null,
    current_week_sale_count integer default null,
    last_week_sale_count integer default null,
    closebook_date datetime default null,
    closebook_perform_date datetime default null,
    restore_date datetime default null,
    company_created_from integer default <k44.VYAPAR enum>   -- app brand that created the company
)
```
Later ALTER-only additions also exist (15 alter-table kb_companies strings), e.g. `add column company_access_status integer default`, `add column current_company_id`, `initial_company_id`, `shared_to_userid`, `sync_company_global_id`, `company_last_accessed_time` — same columns: the CREATE is the consolidated newest-DB version.

### 3.2 kb_firms — firm profile incl. document number counters (0x84ca5d)
```sql
create table kb_firms (
    firm_id integer primary key autoincrement,
    firm_name varchar(256) UNIQUE,
    firm_invoice_prefix varchar(10),
    firm_invoice_number integer default 0,
    firm_tax_invoice_prefix varchar(10),
    firm_tax_invoice_number integer default 0,
    firm_email varchar(256),
    firm_phone varchar(20),
    firm_address varchar(256),
    firm_tin_number varchar(20),
    firm_logo integer default null,
    firm_signature integer default null,
    firm_gstin_number varchar(32) default '',
    firm_state varchar(32) default '',
    firm_bank_name varchar(32) default '',
    firm_bank_account_number varchar(32) default '',
    firm_bank_ifsc_code varchar(32) default '',
    firm_estimate_prefix varchar(10) default '',
    firm_estimate_number integer default 0,
    firm_cash_in_prefix varchar(10) default '',
    firm_delivery_challan_prefix varchar(32) default '',
    firm_upi_bank_account_number varchar(32) default '',
    firm_upi_bank_ifsc_code varchar(32) default '',
    firm_business_type integer default 0,
    firm_business_category varchar(50) default '',
    firm_description varchar(256) default '',
    firm_visiting_card integer default null,
    firm_invoice_printing_bank_id integer default null,
    firm_collect_payment_bank_id integer default null,
    firm_pincode varchar(10) default '',
    firm_dispatch_address varchar(2000) default '',
    firm_dispatch_pincode varchar(10) default '',
    firm_phone_secondary varchar(20),
    firm_pg_linked_account_id integer default null,
    firm_business_identifier_json text default null,
    firm_additional_details_json text default null,
    foreign key (firm_logo) references kb_images(image_id),
    foreign key (firm_visiting_card) references kb_images(image_id),
    foreign key (firm_signature) references kb_images(image_id),
    foreign key (firm_invoice_printing_bank_id) references kb_paymentTypes(paymentType_id) on delete set default,
    foreign key (firm_collect_payment_bank_id) references kb_paymentTypes(paymentType_id) on delete set default,
    foreign key (firm_pg_linked_account_id) references kb_paymentTypes(paymentType_id)
)
```

### 3.3 kb_names — parties (0x84f942 + tail from `Lddg;` clinit)
```sql
create table kb_names(
    name_id integer primary key autoincrement,
    date_created datetime default CURRENT_TIMESTAMP,
    date_modified datetime default CURRENT_TIMESTAMP,
    full_name varchar(50),
    phone_number varchar(11),
    email varchar(50),
    amount double,                    -- running balance due
    date_remindon datetime, date_sendsmson datetime, date_ignoretill datetime,
    address varchar(2000),
    name_type integer default 1,      -- Customer / Supplier / Pay&Receive / Expense / OtherIncome / Cash…
    name_group_id integer default 1,
    name_tin_number varchar(20) default '',
    name_gstin_number varchar(32) default '',
    name_state varchar(32) default '',
    name_shipping_address varchar(2000) default '',
    name_customer_type integer default 0,
    is_party_details_sent integer default 0,
    name_last_txn_date datetime default null,
    name_expense_type varchar(32) default null,
    name_verified_gstin integer default 0,
    created_by integer default null references urp_users(user_id),
    updated_by integer default null references urp_users(user_id),
    pincode varchar(10) default '',
    name_shipping_pincode varchar(10) default '',
    credit_limit integer default null,
    credit_limit_enabled integer default 0,
    frequency_of_payment_reminder integer default 1,
    party_billing_name varchar(50) default null,
    name_is_active integer not null default 1,
    name_sub_type integer default <xcg.GENERAL enum>,   -- B2B/B2C etc.
    name_country varchar(32) default null,
    name_business_identifier_json text default null,
    unique(full_name, name_type),
    foreign key(name_group_id) references kb_party_groups(party_group_id)
)
```
kb_address (0x84b039): `address_id PK, name_id not null FK→kb_names, address_type integer default 1, address varchar(2000) not null default '', date_created/date_modified, address_line_1, address_line_2, city, pincode, state_name, country, unique(name_id,address_type,address)`.

### 3.4 kb_items (0x84e472)
```sql
create table kb_items(
    item_id integer primary key autoincrement,
    item_name varchar(256),
    item_sale_unit_price double,
    item_purchase_unit_price double,
    item_stock_quantity double default 0,
    item_min_stock_quantity double default 0,
    item_location varchar(256) default '',
    item_stock_value double default 0,
    item_date_created datetime default CURRENT_TIMESTAMP,
    item_date_modified datetime default CURRENT_TIMESTAMP,
    item_type integer default 1,          -- Goods/Service/Assembly…
    category_id integer default null,
    item_code varchar(32) unique default null,   -- also used as scanned barcode value
    base_unit_id integer references kb_item_units(unit_id),
    secondary_unit_id integer references kb_item_units(unit_id),
    unit_mapping_id integer references kb_item_units_mapping(unit_mapping_id),
    item_hsn_sac_code varchar(32) default '',
    item_tax_id integer default null references kb_tax_code(tax_code_id),
    item_tax_type integer default 2,      -- incl/excl
    item_additional_cess_per_unit double default null,
    item_description varchar(256) default '',
    item_is_active integer default 1,
    item_tax_type_purchase integer default 2,
    item_catalogue_status integer default 0,
    item_catalogue_sale_unit_price double default 0,
    item_catalogue_description varchar(256) default '',
    item_ist_type integer default null,   -- 0 none / batch / serial
    item_discount_type integer default 1,
    item_discount double default 0,
    created_by integer default null references urp_users(user_id),
    updated_by integer default null references urp_users(user_id),
    item_mrp double default null,
    item_dis_on_mrp_for_sp double default null,
    item_dis_on_mrp_for_wp double default null,
    item_wholesale_price double default null,
    item_min_wholesale_qty double default null,
    item_tax_type_wholesale_price integer default 2,
    item_catalogue_stock_status integer not null default 1,
    service_reminder_status integer default null,
    service_period integer default null,
    icf_values varchar default null,
    tax_basis integer not null default 0,
    unique(item_name, item_type)
)
```
Units: `kb_item_units(unit_id PK, unit_name varchar(50) unique, unit_short_name varchar(10) unique, unit_full_name_editable integer default 1, unit_deletable integer default 1)`; `kb_item_units_mapping(unit_mapping_id PK, base_unit_id FK, secondary_unit_id FK, conversion_rate double)`.

### 3.5 kb_transactions — voucher header (0x851de3, 5684 bytes; complete)
```sql
create table kb_transactions (
    txn_id integer primary key autoincrement,
    txn_date_created datetime default CURRENT_TIMESTAMP,
    txn_date_modified datetime default CURRENT_TIMESTAMP,
    txn_name_id integer,                      -- party (FK kb_names)
    txn_cash_amount double,                   -- amount tendered (payments)
    txn_balance_amount double,                -- due
    txn_type integer,                         -- SALE/PURCHASE/PAYIN/… enum
    txn_date date,
    txn_discount_percent double, txn_tax_percent double,
    txn_discount_amount double, txn_tax_amount double,
    txn_due_date date, txn_description varchar(1024),
    txn_image_path varchar(256),
    txn_payment_type_id integer default 1,
    txn_payment_reference varchar(50) default '',
    txn_ref_number_char varchar(50) default '',
    txn_status integer default 1,
    txn_ac1_amount double default 0, txn_ac2_amount double default 0, txn_ac3_amount double default 0,
    txn_firm_id integer default null,
    txn_sub_type integer default 0,
    txn_invoice_prefix varchar(10) default null,
    txn_image_id integer default null,
    txn_tax_id integer default null references kb_tax_code(tax_code_id),
    txn_custom_field text default '',
    txn_display_name varchar(256) default '',
    txn_reverse_charge integer default 0,
    txn_place_of_supply varchar(256) default '',
    txn_round_off_amount double default 0,
    txn_itc_applicable integer default 0,
    txn_po_date date default null, txn_po_ref_number varchar(50) default '',
    txn_return_date datetime default null, txn_return_ref_number varchar(50) default '',
    txn_eway_bill_number varchar(50) default '',
    txn_current_balance double default 0,
    txn_payment_status integer default 1,
    txn_payment_term_id integer default null references kb_payment_terms(payment_term_id) -- via FK clause below
    txn_prefix_id integer default null references kb_prefix(prefix_id),
    txn_tax_inclusive integer default 2,
    txn_billing_address text default '', txn_shipping_address text default '',
    txn_eway_bill_api_generated integer default 0, txn_eway_bill_generated_date datetime default null,
    txn_category_id integer default null,     -- FK kb_names (expense/income category party)
    txn_time integer not null default 0,
    txn_online_order_id varchar(50) default null,
    created_by integer default null references urp_users(user_id),
    updated_by integer default null references urp_users(user_id),
    txn_paymentgateway_paymenttype_id integer default null,
    txn_paymentgateway_payment_txn_id text default null,
    txn_paymentgateway_link text default null,
    txn_paymentgateway_qr text default null,
    txn_tcs_tax_id integer default null references kb_tcs_tax_rates(tcs_tax_id),
    txn_tcs_tax_amount double default 0,
    txn_irn_number varchar(256) default null,
    txn_einvoice_qr varchar default null,
    txn_discount_type integer default 0,
    store_id integer default null references stores(id),
    ac1_name varchar(1024) default null, ac2_name …, ac3_name …,
    ac1_sac_code …, ac2_sac_code …, ac3_sac_code …,
    ac1_tax_amount …, ac2_tax_amount …, ac3_tax_amount …,
    ac1_itc_applicable …, ac2_, ac3_ …,
    ac1_tax_id integer default null references kb_tax_code(tax_code_id), ac2_tax_id, ac3_tax_id,
    mobile_no varchar default null,
    loyalty_amount double not null default 0,
    cancelled_einvoice_date datetime default null,
    txn_tds_tax_id integer default null references tds_tax_rates(id),
    txn_tds_tax_amount double default 0.0,
    payment_initiated_deviceId varchar default null,
    icf_names varchar default null,
    latitude double default null, longitude double default null,
    additional_details_json text default null,
    tnc_id integer default null references terms_and_conditions(tnc_id),
    currency_id integer default null references currencies(id),
    exchange_rate double default null,
    txn_country varchar(32) default null,
    foreign key(txn_name_id) references kb_names(name_id),
    foreign key(txn_tcs_tax_id) references kb_tcs_tax_rates(tcs_tax_id),
    foreign key(txn_category_id) references kb_names(name_id),
    foreign key(txn_firm_id) references kb_firms(firm_id),
    foreign key(txn_paymentgateway_paymenttype_id) references kb_paymentTypes(paymentType_id),
    foreign key(txn_payment_term_id) references kb_payment_terms(payment_term_id),
    foreign key(store_id) references stores(id),
    foreign key(txn_tds_tax_id) references tds_tax_rates(id),
    foreign key(tnc_id) references terms_and_conditions(tnc_id),
    foreign key(currency_id) references currencies(id)
)
```
(`txn_ref_number integer default 0` exists only via `alter table kb_transactions add txn_ref_number integer default 0` — legacy numeric invoice number, superseded by `txn_ref_number_char`.)

### 3.6 kb_lineitems (0x84ee83)
```sql
create table kb_lineitems (
    lineitem_id integer primary key autoincrement,
    lineitem_txn_id integer,                  -- FK kb_transactions(txn_id)
    item_id integer,                          -- FK kb_items(item_id)
    quantity double, priceperunit double, total_amount double,
    lineitem_tax_amount double default 0,
    lineitem_discount_amount double default 0,
    lineitem_unit_id integer references kb_item_units(unit_id),
    lineitem_unit_mapping_id integer references kb_item_units_mapping(unit_mapping_id),
    lineitem_tax_id integer default null references kb_tax_code(tax_code_id),
    lineitem_mrp double default null,
    lineitem_batch_number varchar(30) default '',
    lineitem_expiry_date datetime default null,
    lineitem_manufacturing_date datetime default null,
    lineitem_serial_number varchar(30) default '',
    lineitem_count double default null,       -- composite-unit count
    lineitem_description varchar(1024) default '',
    lineitem_additional_cess double default null,
    lineitem_total_amount_edited integer default 0,
    lineitem_itc_applicable integer default 0,
    lineitem_size varchar(100) default '',
    lineitem_ist_id integer default null,     -- FK kb_item_stock_tracking(ist_id)
    lineitem_free_quantity double default 0,
    lineitem_discount_percent double default 0,
    lineitem_is_serialized double default 0,
    lineitem_fa_cost_price double default 0,  -- assembly cost basis
    lineitem_discount_type integer default 0,
    lineitem_ref_id varchar(10),
    lineitem_txn_po_ref_number varchar(50) default null,
    icf_values varchar default null,
    tax_basis integer not null default 0,
    foreign key(lineitem_txn_id) references kb_transactions(txn_id),
    foreign key(item_id) references kb_items(item_id),
    foreign key(lineitem_ist_id) references kb_item_stock_tracking(ist_id)
)
```

### 3.7 Invoice number series
- `kb_prefix` (0x850ef1): `prefix_id PK, prefix_firm_id FK→kb_firms, prefix_txn_type integer, prefix_value varchar(50) default '', prefix_is_default integer default 0, unique(prefix_firm_id,prefix_txn_type,prefix_value)`.
- Next number for a series = `max` over `kb_transactions.txn_invoice_prefix`/`txn_ref_number_char` joined with `txn_prefix_id`; firm-level counters `firm_invoice_number`, `firm_tax_invoice_number`, `firm_estimate_number` (kb_firms) seed the defaults.

### 3.8 Batches / serials / expiry / stock
- `kb_item_stock_tracking` (0x84df70): `ist_id PK, ist_batch_number varchar(30) default '', ist_serial_number varchar(30) default '', ist_mrp double, ist_expiry_date datetime, ist_manufacturing_date datetime, ist_size varchar(100), ist_item_id FK kb_items, ist_current_quantity double, ist_opening_quantity double, ist_type integer default 0` — one row per batch (or serial) with its own quantity; item totals roll up in `kb_items.item_stock_quantity`.
- `kb_serial_details` (0x8510b6): `serial_id PK, serial_item_id FK, serial_number varchar(256), serial_current_quantity double`; `kb_serial_mapping` (0x8511f0): links serial rows to lineitems/adjustments.
- Barcodes: **no barcode table** — scanner matches `kb_items.item_code` (unique); settings gate via `VYAPAR.BARCODESCANNINGENABLED`.

### 3.9 Taxes / TCS / TDS / charges
- `kb_tax_code` (0x8518c7): `tax_code_id PK, tax_code_name varchar(32) unique, tax_rate double, tax_code_type integer default 0 (GST/…), tax_rate_type integer default 4 (per-unit slabs), created/modified`.
- `kb_tax_mapping`: group_id/code_id both FK kb_tax_code (composite slabs).
- `kb_tcs_tax_rates` (0x851c4f): `tcs_tax_id PK, tcs_tax_name unique NOT NULL, tcs_tax_percentage double, tcs_tax_nature_of_collection_id integer default 1, created/modified`.
- `tds_tax_rates` (0x85583a): `id PK, name unique NOT NULL, percentage double NOT NULL, section_id integer NOT NULL, created/modified`.
- `kb_extra_charges` (0x84c8e8): `extra_charges_id PK, extra_charges_name varchar(1024), sac_code varchar(32), tax_enabled, enabled, tax_id FK kb_tax_code`.

### 3.10 Payments
- `kb_paymentTypes` (0x8502fe): `paymentType_id PK, paymentType_type varchar(30) default 'BANK', paymentType_name varchar(30) unique, paymentType_bankName, paymentType_accountNumber, paymentType_opening_balance double, paymentType_opening_date, pt_bank_ifsc_code, pt_bank_account_holder_name, pt_bank_upi_id, pt_bank_ref_id, pt_bank_code, edc_deviceId, edc_vendor_type, edc_device_type, uuid, pg_linked_account_id self-FK, pg_vendor_type, pg_metadata text, pt_extra_fields text`.
- `txn_payment_mapping` (0x85618f): `id PK, payment_id FK, cheque_id FK kb_cheque_status, txn_id FK, amount double, payment_reference, edc_payment_status/txnId/mode/initiationId/card_last_digits, sqr_txn_id`.
- `kb_cheque_status`, `kb_bank_adjustments`, `kb_cash_adjustments`, `kb_bank_accounts` as in catalog above (full text in /tmp catalog, extracted verbatim).

### 3.11 Manufacturing (assembly)
- `item_def_assembly` (0x84a63e): BOM rows — `assembled_item_id` + `def_assembly_item_id` both FK kb_items, qty, unit/unit-mapping FKs, timestamps.
- `item_def_assembly_additional_costs` (0x84a25e…): AC1–5 doubles + payment type FK.
- `item_mfg_assembly_additional_costs` (0x84acb8): per manufacture-adjustment: `mfg_adj_id FK kb_item_adjustments`, payment type/ref, AC1–5, `mfg_txn_id FK kb_transactions`.
- Manufacture movements are `kb_item_adjustments` rows with `item_adj_mfg_adj_id` self-FK (0x84d535 full text in catalog).

### 3.12 Loyalty
`loyalty_txn` (0x8544e4): `id PK, txn_id unique FK kb_transactions, party_id FK kb_names, mobile_no, point_rewarded, point_redeemed, amount, txn_type, redeemed_date, rewarded_date, created/updated, created_by/updated_by FK urp_users`.

### 3.13 Attachments / images
- `attachments` (0x838e0d): `id PK autoinc, url varchar(256) not null, name varchar(1024) not null`.
- `transaction_attachments` (0x855ce0): `id PK, txn_id FK cascade, uuid varchar(50) unique NOT NULL, name varchar(1024) NOT NULL`.
- `task_attachments`, `item_adj_attachment_mappings`, `item_adj_image_mappings` mapping tables (full text in catalog).
- `kb_images(image_id PK, image_bitmap BLOB)`; `kb_item_images(item_image_id PK, item_id FK, item_image_bitmap BLOB, catalogue_item_id FK, item_image_catalogue_sync_status, item_image_is_dirty)`.

### 3.14 Users/permissions ("urp" = user role permissions)
- `urp_users` (0x856747): `user_id PK, user_name varchar(64) not null, user_role_id, user_passcode varchar(64), user_phone_or_emaill, user_is_active, user_is_deleted, user_sync_enabled, user_sync_started, user_status, user_server_id, users_permission text (JSON)`.
- `urp_activity`, `urp_item_category_access` (full text in catalog).

### 3.15 V2 accounting set (uppercase DDL, newer module @0x8a5xxx)
`chart_of_accounts_mapping`, `journal_entry`, `journal_entry_line_items`, `other_accounts` — full text in catalog; they FK into core (`kb_firms`, `urp_users`, `kb_transactions`).

---

## 4. Migration model

- 122 business `ALTER TABLE` strings touch only: kb_transactions(33), kb_firms(16), kb_companies(15), kb_names(14), kb_items(13), kb_lineitems(16→counted with case variants), kb_item_adjustments(5), kb_paymentTypes(3), kb_sms(2), kb_bank_adjustments, kb_cheque_status, kb_closed_link_txn_table, kb_item_images, kb_txn_links (1 each).
- Pattern: newest-DB `create table` already contains all columns; ALTERs are for upgrading old DBs; "rename to *_old + recreate + insert-select" (e.g. `Alter table kb_items rename to kb_items_old`, `_new_WorkSpec`-style) for table-shape rewrites. FKs toggled via pragma (trigger check string `SELECT name FROM sqlite_master WHERE type = 'trigger'`).
- Old DB lineage visible: short legacy CREATE variants at 0x916xxx–0x91a2xx (fewer columns) = historical schema steps; the 0x84xxxx block is v-current.

## 5. Appendix — SDK-owned tables (excluded from clone; 59 CREATE strings)

- **WorkManager/Room** (classes.dex, classes2.dex): WorkSpec, WorkTag, WorkName, WorkProgress, Dependency, SystemIdInfo, Preference, _new_WorkSpec/_new_SystemIdInfo, room_master_table.
- **Firestore** (classes.dex): bundles, collection_parents, data_migrations, document_mutations, document_overlays, mutation_queues, mutations, named_queries, remote_documents, target_documents, target_globals, targets, index_configuration, index_entries, index_state.
- **Firebase Realtime DB** (classes.dex): serverCache, trackedKeys, trackedQueries, writes.
- **Play Services / GMS Analytics** (classes2.dex): app2, apps, audience_filter_values, conditional_properties, consent_settings, default_event_params, event_filters, events, main_event_params, property_filters, queue, raw_events, raw_events_metadata, trigger_uris, user_attributes.
- **firebase-messaging** (classes5.dex): messages.
- **Play Services cronet logger** (classes.dex): event_metadata, event_payloads, events(transport), global_log_event_state, log_event_dropped, transport_contexts.
- **reCAPTCHA** (classes2.dex): `ce` (observed: `com.google.android.recaptcha.internal.zzaz.onCreate`).
- **Dart plugin cache** (libapp.so strings): cacheObject (sqflite HTTP cache).

## 6. Clone seed notes

1. Two-DB design (master registry + per-company file) simplifies backup/switch; keep company registry minimal.
2. Party single-table inheritance (kb_names by name_type) — clone can keep it; it is the backbone of balance reports.
3. Vouchers: one wide `kb_transactions` + `kb_lineitems` covers all 20+ voucher types via txn_type/sub_type; balance/due tracked on both txn and party.
4. Batch & serial are quantity-carrier rows (`kb_item_stock_tracking`) referenced from lineitems/adjustments; item totals denormalized.
5. GST fields per level: item slab (tax_code_id + tax_type incl/excl), line tax, txn tax, TCS/TDS, cess per unit, place of supply, reverse charge, e-invoice IRN/QR, e-way bill.
6. Settings as KV + prefix series + firm counters for invoice numbering.
