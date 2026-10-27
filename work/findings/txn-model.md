# txn-model — Vyapar transaction / voucher system (dex-derived)

Target: `in.android.vyapar` v31.0.1 (vc728). All facts below come from the APK's DEX
(`strings` over `dex/raw/classes*.dex` + `venv/bin/droidasc getclass/findrefs` over
`apks/base.apk`). Nothing copied — only structure/behaviour, for a clean-room clone.

---

## 0. Which layer actually owns vouchers (correction to the assumed architecture)

The transaction/voucher system is **NOT** in the Flutter layer.

* `dex/raw/classes*.dex` still contains the full native entry screen:
  `Lin/android/vyapar/NewTransactionActivity;` (9,960 decompiled lines, registered in
  `dex/manifest.txt`, layout `res/layout/activity_new_transaction.xml`), plus
  `ViewOrEditTransactionDetailActivity`, `SelectTransactionActivity`, `EstimateDetailsActivity`,
  `DeliveryChallan/DeliveryChallanConversionActivity`, `expense/ExpenseActivity`,
  `transaction/ui/AdditionalChargeForTxnActivity`, `AddItemUnitMappingActivity`.
* The Dart AOT image is a *separate* embedded product: `dart_out/scripts.jsonl` has only
  160 `package:adkrity_app/...` scripts (create_order / ads / campaign / auth / payment-gateway
  web views) and **zero** scripts matching `txn|transaction|invoice|voucher|item|party|stock|expense`.
* The real "business core" is Java + a Kotlin-Multiplatform shared module whose classes are
  name-obfuscated but whose **toString()/serializer property names survive** (classes3.dex + classes6.dex).
  KMP packages recovered from strings: `vyapar.shared.domain.constants.TxnType`,
  `vyapar.shared.domain.models.BaseLineItem`, `vyapar.shared.legacy.transaction.bizLogic.*`,
  `vyapar.shared.legacy.lineItem.bizLogic.BaseLineItem`, `vyapar.shared.data.models.TransactionPaymentMappingModel`.
* Table DDL is shipped as SQLDelight/Room schema-holder classes (one class per table, e.g. `Ltwo;` =
  `kb_transactions`, `Lm9e;` = `kb_lineitems`), and runtime writes use `ContentValues`
  (`Leco;` writes `kb_transactions`, `kb_lineitems`, `kb_item_stock_tracking`, `kb_party_item_rate`, `kb_fts_vtable`).

Clone implication: a single-process Java/Kotlin (or any-language) implementation of the
`kb_*` schema + the TxnType registry below is sufficient; no Dart needed.

---

## 1. Voucher-type registry (the authoritative list)

Three co-existing enums encode the same vocabulary:

| descriptor | size | role |
|---|---|---|
| `Lvwo;` (classes6.dex) | 31 | **shared/KMP `vyapar.shared.domain.constants.TxnType`**; constant `txnTypeConstant` == DB `txn_type`; has `getTxnTypeConstant()`, `sortByCreationDateDescendingTxnTypes` |
| `Lywo;` (classes3.dex) | 26 | Java UI enum: `getTxnName()` (API/print key), `getNum()`, `getNameResId()` |
| `Lzwo;` (classes3.dex) | 27 | legacy UI enum with `toSharedTxnType()` bridging to `vwo` by matching `num` |

Numeric `txn_type` values (from `Lvwo;` static init) and display keys (from `Lywo;`):

| txn_type | enum | display/txnName | notes |
|---|---|---|---|
| 1 | TXN_TYPE_SALE | `sale_txn` | Sale invoice |
| 2 | TXN_TYPE_PURCHASE | `purchase_txn` | Purchase invoice |
| 3 | TXN_TYPE_CASHIN | `payment_in` | Receipt (money in) |
| 4 | TXN_TYPE_CASHOUT | `payment_out` | Payment (money out) |
| 5 | TXN_TYPE_ROPENBALANCE | – | Receivable opening balance |
| 6 | TXN_TYPE_POPENBALANCE | – | Payable opening balance |
| 7 | TXN_TYPE_EXPENSE | `expense_txn` | Expense |
| 21 | TXN_TYPE_SALE_RETURN | `credit_note` | Credit note |
| 23 | TXN_TYPE_PURCHASE_RETURN | `debit_note` | Debit note |
| 24 | TXN_TYPE_SALE_ORDER | `order_form_txn` | Sale order |
| 27 | TXN_TYPE_ESTIMATE | `estimate_txn` | Estimate / quotation |
| 28 | TXN_TYPE_PURCHASE_ORDER | `purchase_order_txn` | Purchase order |
| 29 | TXN_TYPE_OTHER_INCOME | `extra_income_txn` | Other income |
| 30 | TXN_TYPE_DELIVERY_CHALLAN | `delivery_challan_txn` | Delivery challan |
| 31 / 32 | TXN_TYPE_LINKED_CASHIN/CASHOUT_OPENINGBALANCE | – | linked opening balance |
| 50 / 51 | TXN_TYPE_PARTY_TO_PARTY_TRANSFER_RECEIVABLE / _PAID | `party_to_party_received/paid` | party-to-party transfer |
| 60 / 61 | TXN_TYPE_SALE_FA / TXN_TYPE_PURCHASE_FA | `sale_fa_txn`/`purchase_fa_txn` | fixed-asset sale/purchase |
| 65 | TXN_TYPE_CANCELLED_SALE | `cancelled_sale_txn` | cancelled sale |
| 67 / 68 | TXN_TYPE_LOYALTY_ADD/REDUCE_POINTS | `point_adjustment` | loyalty |
| 90 | TXN_TYPE_LOYALTY_OPENING_BALANCE | `opening_balance` | loyalty opening |
| 70 | TXN_TYPE_JOB_WORK_CHALLAN | `jw_out_challan` | job-work outward challan |
| 71 | TXN_TYPE_PURCHASE_JOB_WORK | `purchase_jw` | job-work purchase |
| 81 / 82 | TXN_TYPE_JOURNAL_ENTRY_RECEIVED / _PAID | `journal_entry_received/paid` | Journal entries |
| 83 | TXN_TYPE_PROFORMA_INVOICE | `proforma_invoice_txn` | Proforma invoice |
| -1 | TXN_TYPE_NONE | "" | sentinel |
| -917 | TXN_TYPE_SALE_REPEATING_FOR_FILTERS | – | report-filter pseudo type |

Additional txn-type numbers appear only in raw SQL (`qc_txn_type in (1,12,23,53)`, `item_adj_type=10`),
i.e. 12/53 are stock-adjustment/transfer pseudo-vouchers stored elsewhere
(`kb_item_adjustments`, `kb_bank_adjustments`, `kb_cash_adjustments`).

**Not voucher types** (explicitly checked): there is *no* `SUPPLY_BILL`, `CONTRA`, `JOURNAL` (as txn_type)
or `DRAFT` type. "Bill of Supply" is a print heading for non-tax invoices
(`"Bill of Supply"`, `VYAPAR.PRINTBILLOFSUPPLYFORNONTAXTXN`); Contra is realised as Cash In/Out (3/4)
+ `kb_bank_adjustments`; Journal is realised as txn_type 81/82 *and* the separate KMP accounting
tables `journal_entry` / `journal_entry_line_items` / `other_accounts` / `chart_of_accounts_mapping`.

Per-voucher UI model classes (recovered from Kotlin `toString()` prefixes in classes6.dex):
`SaleOrderTransaction(`, `PurchaseOrderTransaction(`, `SaleReturnTransaction(`, `PurchaseReturnTransaction(`,
`DeliveryChallanTransaction(`, `ExpenseTransaction(`, `OtherIncomeTransaction(`, `JobWorkTransaction(`,
`MoneyInTransaction(`, `MoneyOutTransaction(`, `P2PPaidTransaction(`, `P2PReceivedTransaction(`,
`LoanTransaction(`, `BankAdjustmentTxn(`, `ItemAdjustmentTxn(`, `StockTransferTxn(`,
`BeginningBalancePayableTransaction(`, `FixedAssetDetailTxn(`, `RecycleBinTxn(`,
`AdditionalChargeForTxn(`, `DummyBillerCombinedTransaction(`, and the generic `KbTransaction(`.

---

## 2. Voucher header: `KbTransaction` / `kb_transactions`

`Ldlj$d;->toString()` enumerates the in-memory header (89 properties, exact names):
`txnDateCreated, txnNameId, txnPartyName, txnCashAmount, txnBalanceAmount, txnType, txnDate,
txnDiscountPercent, txnTaxPercent, txnDiscountAmount, txnTaxAmount, txnDueDate, txnDescription,
txnPaymentTypeId, txnPaymentTypeName, txnPaymentReference, txnRefNumberChar, txnStatus,
txnAc1Amount, txnAc2Amount, txnAc3Amount, txnFirmId, txnSubType, txnInvoicePrefix, txnTaxId,
txnCustomField, txnDisplayName, isTxnReverseCharge, txnPlaceOfSupply, txnCountry, txnRoundOfAmount,
txnItcApplicable, txnPoDate, txnPoRefNumber, txnReturnDate, txnReturnRefNumber, txnEwayBillNumber,
txnCurrentBalance, txnPaymentStatus, txnPaymentTermId, paymentTermName, txnPrefixId, txnTaxInclusive,
txnBillingAddress, txnShippingAddress, txnEwayBillApiGenerated, txnEwayBillGeneratedDate,
txnCategoryId, txnCategoryName, txnPartyExpenseType, txnTime, txnOnlineOrderId, createdBy, updatedBy,
txnUdfList, qrPaymentGateway, linkPaymentGateway, bankIdPaymentGateway, paymentGatewayTxnId,
txnTcsTaxId, txnTcsTaxAmt, attachmentList, storeId, ac1Name..ac3Name, ac1SacCode..ac3SacCode,
ac1TaxId..ac3TaxId, ac1TaxAmount..ac3TaxAmount, ac1ItcApplicable..ac3ItcApplicable, loyaltyAmount,
txnMobileNumber, txnCancelledEInvoiceDate, txnIrnNumber, txnEInvoiceQr, txnTdsTaxId, txnTdsTaxAmt,
txnIcfNames, latitude, longitude, txnAdditionalDetails, txnTncId, currencyId, exchangeRate`.

DDL owner: `Ltwo;` (`create table kb_transactions …`, ~95 columns). Key structural rules:

* **Party & category are foreign keys into one table** `kb_names`: `txn_name_id` (party) and
  `txn_category_id` (expense / other-income category) both `references kb_names(name_id)`.
* **Money decomposition** (used pervasively in the shipped report SQL):
  `txn_cash_amount` = amount settled on the voucher itself, `txn_balance_amount` = outstanding,
  `loyalty_amount` = value redeemed, so voucher total ≈ `txn_cash_amount + txn_balance_amount + loyalty_amount`
  (sales add loyalty; purchases do not, per `CASE txn_type WHEN 1 THEN sum(txn_balance_amount+txn_cash_amount+loyalty_amount) …`).
  `txn_current_balance` is the running party balance snapshot;
  a migration computes it for receipts/payments:
  `update kb_transactions set txn_current_balance = (ifnull(txn_cash_amount,0) + ifnull(txn_discount_amount, 0)) where txn_type in (3, 4)`.
* Tax: `txn_tax_id → kb_tax_code`, `txn_tax_percent`, `txn_tax_amount`, `txn_tax_inclusive` (default 2),
  `txn_reverse_charge`, `txn_itc_applicable`, `txn_tcs_tax_id/amount → kb_tcs_tax_rates`,
  `txn_tds_tax_id/amount → tds_tax_rates`, cess folded into `txn_tax_amount` + per-line `lineitem_additional_cess`.
* Numbering: `txn_ref_number_char` + `txn_prefix_id → kb_prefix(prefix_id)`;
  `kb_prefix` is `(prefix_firm_id, prefix_txn_type, prefix_value, prefix_is_default)` with
  `unique(prefix_firm_id, prefix_txn_type, prefix_value)` → **per-firm, per-voucher-type prefixes**.
  `txn_invoice_prefix` is a legacy duplicate column kept in the schema.
* Multi-firm / multi-store / multi-user / multi-currency scoping: `txn_firm_id → kb_firms`,
  `store_id → stores(id)`, `created_by/updated_by → urp_users(user_id)`,
  `currency_id → currencies(id)` + `exchange_rate` + `txn_country`, `tnc_id → terms_and_conditions`.
* GST compliance: `txn_eway_bill_number`, `txn_eway_bill_api_generated`, `txn_eway_bill_generated_date`,
  `txn_irn_number`, `txn_einvoice_qr`, `cancelled_einvoice_date`, `txn_place_of_supply`,
  `additional_details_json` (KMP `vyapar.shared.data.models.TxnAdditionalDetails` =
  `{ackNo, ackDt, repeatInvoiceTemplateId}` — e-invoice ACK + repeating-invoice template link).
* Misc: `txn_custom_field`/`icf_names` (user-defined fields), `txn_online_order_id` (catalogue orders),
  `latitude`/`longitude`, `txn_time` (time-of-day, for GST/hour-wise reports), `txn_display_name`,
  `mobile_no`, `txn_description` varchar(1024), `txn_billing_address`/`txn_shipping_address`.
* Payment-gateway: `txn_paymentgateway_paymenttype_id → kb_paymentTypes`, `txn_paymentgateway_payment_txn_id`,
  `txn_paymentgateway_link`, `txn_paymentgateway_qr`.

### Sub-types
`txn_sub_type integer default 0` exists and is carried in every list query
(`select txn.txn_id … txn.txn_sub_type …`) plus mirrored `name_sub_type` on `kb_names`, and
KMP logs `txnSubType=`/`txnSubTypeNum=` next to `txnStatus` — i.e. a second-level classification per
voucher family (e.g. cancelled/return/repeat variants). Exact constant table was **not** recoverable
from dex (only `PointsTxnUiModel(txnId=…, txnType=…, txnTypeNum=…, txnSubTypeNum=…, txnStatus=<enum c2f>…)`) —
clone should model it as an int with per-voucher semantics.

---

## 3. Status / lifecycle fields (observed semantics)

| field | default | observed predicates (from shipped SQL) | reading |
|---|---|---|---|
| `txn_status` | 1 | `txn_status != 4` (universal filter), `txn_status = 4 OR txn_status = 5`, `T.txn_status in (2,1,5)`, `(item_adj_txn_id is null or txn_status != 2)` | 1 = normal/active; 2 = a "not-yet-final/linked" state excluded from adjustments; 4 = deleted (excluded everywhere); 5 = a second soft-deleted/alternate state counted only in recycle-bin style unions |
| `txn_payment_status` | 1 | `where txn_payment_status = 0`, `set txn_payment_status = 3 where txn_payment_status = 0 and txn_type = 7`, `txn_payment_status in (…)` + filter string `TXN_PAYMENT_STATUS_FILTER_LIST` | paid/part-paid/unpaid style enum; 0 is a legacy value migrated to 3 |
| `txn_due_date` | – | `update kb_transactions set txn_payment_term_id = 1, txn_due_date = txn_date where txn_type not in (24,28,30)` | due date + payment term are only meaningful on Sale Order (24), Purchase Order (28), Delivery Challan (30) |
| `txn_payment_term_id` | null | `→ kb_payment_terms`; seeded `1 'Due On Receipt' 0 (default), 2 Net 15, 3 Net 30, 4 Net 45, 5 Net 60`; gated by setting `SETTING_DUE_DATE_AND_PAYMENT_TERM_ENABLED` | due date = voucher date + `term_days` |
| cheque | – | `kb_cheque_status(cheque_txn_id, cheque_current_status default 0, cheque_transfer_date, cheque_close_desc, transferred_To_Account, check_creation_date, check_modification_date, cheque_closed_txn_ref_id → kb_closed_link_txn_table, cheque_issued_date)`; strings `CLOSED_TXN_CHECQUES`, `PAID_CHEQUES`, `UNPAID_UNUSED`, `ERROR_CLOSED_LINK_TXN_*` | cheque/deferred-payment instrument with its own status machine and "closed link" record |
| deletion | – | `recycle_bin(id, txn_deleted_date, txn_firm_id, txn_data_json, txn_type, txn_date, status default 0)`; strings `RecycleBinTxnObj`, `Restoration_was_conflicting`, `setPreDataForRecycleBinTxn`, `isRestoringTxn` (line-item flag) | soft delete = full JSON snapshot of the voucher in `recycle_bin`, restore re-enters `NewTransactionActivity` |
| e-invoice / e-way | 0 | `transaction_attributes(txn_id UNIQUE → kb_transactions ON DELETE CASCADE, einvoice_id, einvoice_status default 0, einvoice_metadata)` | per-voucher compliance side-record, 1:1 |

`NewTransactionActivity` analytics/behaviour strings that define the lifecycle surface:
`ADD_TXN`, `NEW_TXN`, `EDIT_TXN`, `EDIT_OR_CONVERT_TXN`, `DELETE_RB_TXN`, `CONTACT_SUPPORT_ADD_TXN`,
`ERROR_TXN_SAVE_SUCCESS/FAILED`, `ERROR_TXN_DELETE_SUCCESS/FAILED`, `ERROR_TXN_LINK_SAVE_*`,
`ERROR_TXN_REFNO_ALREADY_USED` (ref-number uniqueness), `ERROR_TXN_NEGATIVE_TOTAL_AMOUNT`,
`ERROR_TXN_TOTAL_LESS_THAN_CASH`, `ERROR_TXN_INVALID_AMOUNT`, `ERROR_TXN_ITEM_NOT_ALLOWED`,
`PARTY_CREDIT_LIMIT` + `Credit_limit_alert_view` (credit-limit guard on save),
`FreeInvoiceLimitReached`, `txn_cancelled_because_limit_exceed_alert` (plan-limit guard).

### Conversion (converted-from / converted-to)
Two link tables (plus a third for closed links):

* `kb_linked_transactions(linked_id, txn_source_id → kb_transactions, txn_destination_id → kb_transactions, txns_linked_date)`
  — the conversion edge; `Select txn_destination_id from kb_linked_transactions where txn_source_id = …`,
  `JOIN kb_lineitems li ON li.lineitem_txn_id = klt.txn_destination_id`.
* `kb_txn_links(txn_links_id, txn_links_txn_1_id, txn_links_txn_2_id, txn_links_amount,
  txn_links_txn_1_type, txn_links_txn_2_type, txn_links_closed_txn_ref_id)` — payment/settlement links
  between two vouchers with a partial amount (`txn_links_amount`); KMP model
  `vyapar.shared.legacy.transaction.bizLogic.TransactionLinks` with serializer fields
  `txnLinkId, txnLinkTxn1Id, txnLinkTxn2Id, txnLinkAmount, txnLinkTxn1Type, txnLinkTxn2Type, txnLinkClosedTxnRefId`.
* `kb_closed_link_txn_table(closed_link_txn_id, closed_link_txn_amount/type/date/ref_number,
  txn_links_closed_txn_name_id → kb_names, txn_links_closed_txn_category_id → kb_names)` +
  `vyapar.shared.legacy.transaction.models.ClosedLinkTxnModel` — the historical record of a link that was
  fully closed/voided.

Concrete conversions (event/label strings): `estimate_to_sale`, `estimate_to_sale_order`
(`ESTIMATE_CONVERT_TO_SALE`, `ESTIMATE_CONVERT_TO_SALE_ORDER`), `sale_order_to_sale`,
`proforma_invoice_to_sale`, `proforma_invoice_to_sale_order`, `delivery_challan_to_sale`
(`activity_convert_delivery_challan`, `dc_btn_converted_without_invoice`, `sale_converted_to_delivery_challan_pdf`),
`convert_to_purchase_ripple_effect`, `add_items_to_sale`, `add_asset_to_sale`.
Intent/analytics keys in `NewTransactionActivity`: `txn_to_be_linked`, `duplication_txn_id`,
`arg_transaction_type`, `arg_firm_id`, `initial_route`, `entry_source`, `txn_prefill_data`/`prefill_txn_data`,
`Txn_open_source`, `opened_from_online_orders`, `setPreDataForDuplicateTxn`, `setPreDataForTxn`,
`setPreDataForBill2Bill` (bill-to/ship-to prefill). API query flag `is_converted=` / `is_converted=false`
and print-template variable `Converted_from`; UI flags `isConvertedToSale`, `isSaleConverted`,
report names `getTotalSaleConverted`, `totalSaleConverted`, and settings `SETTING_ORDER_FORM_ENABLED`,
`SETTING_ESTIMATE_ENABLED`, `SETTING_PROFORMA_ENABLED`.
Also `vyapar.shared.legacy.orderingList.LinkedTxnDetails` and
`vyapar.shared.domain.models.TransactionPrefillModel` / `TxnPrefillData`.

---

## 4. Line-item model: `BaseLineItem` / `kb_lineitems`

`Lew1;->toString()` (KMP `vyapar.shared.domain.models.BaseLineItem`) property list:
`lineItemId, itemId, transactionId, itemName, itemQuantity, itemUnitPrice, lineItemTotal, lineItemTax,
lineItemDiscount, lineItemUnitId, lineItemUnitMappingId, lineItemTaxId, lineItemMRP, lineItemBatchNumber,
lineItemExpiryDate, lineItemManufacturingDate, lineItemSerialNumber, lineItemCount, lineItemDescription,
lineItemAdditionalCESS, lineItemTotalAmountEdited, lineItemITCApplicable, lineItemIstId, lineItemSize,
freeReturnQuantity, returnQuantity, lineItemFreeQty, lineItemSerialList, isReturnedQtyRelatedIssue,
itemMainMrp, isWholesalePriceApplied, faCostValue, isUnitDeleted, isRestoringTxn, lineItemReturnedSerialList,
isLineItemSerialized, discountPercentage, priceFromUi, txnTaxTypeForItem, lineItemTxnPoRefNumber,
lineItemIcfValues, taxBasis`.
(Java legacy twin: `vyapar.shared.legacy.lineItem.bizLogic.BaseLineItem`, and `Lgw1;`/`Lfw1;` also print it;
`Leco;` instantiates `gw1` per row from the cursor and stores the list on the voucher as field `M`.)

`Lm9e;` DDL (`create table kb_lineitems`, 33 columns) — persistence shape:
`lineitem_id PK, lineitem_txn_id → kb_transactions, item_id → kb_items, quantity double,
priceperunit double, total_amount double, lineitem_tax_amount, lineitem_discount_amount,
lineitem_unit_id → kb_item_units, lineitem_unit_mapping_id → kb_item_units_mapping,
lineitem_tax_id → kb_tax_code, lineitem_mrp, lineitem_batch_number varchar(30), lineitem_expiry_date,
lineitem_manufacturing_date, lineitem_serial_number varchar(30), lineitem_count double,
lineitem_description varchar(1024), lineitem_additional_cess, lineitem_total_amount_edited integer,
lineitem_itc_applicable integer, lineitem_size varchar(100), lineitem_ist_id → kb_item_stock_tracking,
lineitem_free_quantity double, lineitem_discount_percent double, lineitem_is_serialized double,
lineitem_fa_cost_price double, lineitem_discount_type integer, lineitem_ref_id varchar(10),
lineitem_txn_po_ref_number varchar(50), icf_values, tax_basis integer not null default 0`.

Modelling notes (from shipped SQL — directly reusable in a clone):
* `total_amount` is the **pre-tax, post-line-discount net** value; report SQL re-derives
  `before_discount = priceperunit * quantity`, `discount_amount = lineitem_discount_amount + txn_discount_percent*total_amount/100`,
  `taxable_value = total_amount - txn_discount_percent*total_amount/100`,
  `total_with_tax = taxable_value + taxable_value*tax_percent/100`.
* `lineitem_free_quantity` is a first-class qty dimension: stock and value queries use
  `SUM(quantity + lineitem_free_quantity)` and `qc_qty = quantity + lineitem_free_quantity`.
* `lineitem_total_amount_edited` marks a manually overridden row price (the "rate edited" flag).
* `lineitem_ist_id` binds the row to a specific batch/serial stock record; `lineitem_ref_id`/
  `lineitem_txn_po_ref_number` carry the ordered-row back-reference used by conversions.
* `tax_basis` + `lineitem_itc_applicable` per row (GST "value vs qty based cess" and purchase ITC).

---

## 5. Multi-payment (`multiplepayment` package + `txn_payment_mapping`)

* Native widgets: `Lin/android/vyapar/multiplepayment/PaymentView;`,
  `PaymentView$PaymentViewListener`, `PaymentViewAdapter$IMultiplePayment`,
  `PaymentView$paymentViewAdapter$1`. `PaymentView` holds `LinkedHashMap` ×2 (payment-type → amount and
  payment-type → reference), `ArrayList`s, a running total (`M0/U0/V0/Z0` doubles) and
  `PaymentViewListener` — i.e. a repeatable "payment mode + amount (+ reference)" sub-form embedded in the voucher.
* Persistence: `create table txn_payment_mapping (id PK, payment_id NOT NULL → kb_paymentTypes,
  cheque_id → kb_cheque_status ON DELETE CASCADE, txn_id NOT NULL → kb_transactions ON DELETE CASCADE,
  amount double default 0, payment_reference varchar default '')`, extended in a later schema with
  `edc_payment_status, edc_payment_txnId, edc_payment_mode, edc_payment_initiationId,
  edc_card_last_digits, sqr_txn_id` (POS terminal / SoftPOS linkage).
* KMP models: `vyapar.shared.legacy.transaction.bizLogic.TransactionPaymentMappingModel` and
  `vyapar.shared.data.models.TransactionPaymentMappingModel`; the runtime class `Lmco;` prints
  `…, paymentReference=…, edcPaymentStatus=…, chequeIssuedDate=…, sqrTxnId=…`
  (14 fields). Also `vyapar.shared.data.models.PaymentEntryDto` / `PaymentEntryResult` / `PaymentTypeExtraFields`.
* Payment mode dictionary: `create table kb_paymentTypes(paymentType_id PK, paymentType_type varchar(30)
  DEFAULT 'BANK', paymentType_name varchar(30) UNIQUE, paymentType_bankName, paymentType_accountNumber,
  paymentType_opening_balance, paymentType_opening_date)` seeded with
  `values(1,'CASH','Cash')` and `values(2,'CHEQUE','Cheque')`; everything `payment_id > 2` is a
  user-defined bank/other account (SQL: `WHERE payment_id > 2`, `paymentType_type = 'BANK'`,
  `paymentType_type = 'CASH'`). Voucher header keeps the *primary* mode in
  `txn_payment_type_id` (default 1) + `txn_payment_reference`, while the extra rows live in
  `txn_payment_mapping` (many per voucher). `layout/new_txn_party_details_0` +
  string `SHOW_PAYMENT_BREAKUP` and `transaction_payment_types` cover the UI.
* Header-side payment fields: `txn_cash_amount` (settled here), `txn_balance_amount` (due),
  `txn_payment_status`, `txn_loyalty_amount`/`loyalty_amount` (points used, another settlement leg),
  `ADDED_PAYMENT_INFO`, `ERROR_PAYMENT_TERM_NOT_UNIQUE`, `ERROR_LINKED_PAYMENT_PRESENT`
  (blocks deleting/modifying a voucher that already has payment links).

---

## 6. Discounts

Three independent discount levels, all percent-or-amount:
* **Voucher level**: `txn_discount_percent`, `txn_discount_amount`, `txn_discount_type integer default 0`
  (+ setting `SETTING_DISCOUNT_IN_MONEY_TXN`).
* **Line level**: `lineitem_discount_amount`, `lineitem_discount_percent`, `lineitem_discount_type integer default 0`.
* **Item master level**: `item_discount_type integer default 1`, `item_discount`,
  `item_dis_on_mrp_for_sp` / `item_dis_on_mrp_for_wp` (sale/wholesale price expressed as discount on MRP),
  `item_wholesale_price` + `item_min_wholesale_qty` + `item_tax_type_wholesale_price`
  (wholesale pricing tiers), `item_mrp`/`lineitem_mrp`, and strings
  `MRP_based_tax_Constraints`, `isWholesalePriceApplied`, `SETTING_ITEM_WHOLE_SALE_PRICE`.
* Discount-type enum: `PERCENT` / `AMOUNT` (`DISCOUNT_PERCENT`, `DISCOUNT_AMOUNT`,
  `discountType=`, `discount_amount`, `discount_percent`, `add_discount`, `before_discount`).
* Dedicated screens/reports: `Lin/android/vyapar/DiscountReport;` (extends `AutoSyncBaseReportActivity`,
  emits an HTML `<h2 …><u>Discount Report</u></h2>` from a `List<xfn>` of per-item discount rows),
  `in.android.vyapar.newreports.itemwiseDiscountReport.*`, `report_iwd_total_discount_amount`,
  `card_view_discount_total_amount`, `average_discount_percent`.

---

## 7. Additional charges (and "packaging")

* Header carries **exactly three** charges: `txn_ac1_amount`, `txn_ac2_amount`, `txn_ac3_amount` with
  `ac1_name`/`ac2_name`/`ac3_name`, `ac1_sac_code`..`ac3_sac_code`, `ac1_tax_id`..`ac3_tax_id → kb_tax_code`,
  `ac1_tax_amount`..`ac3_tax_amount`, `ac1_itc_applicable`..`ac3_itc_applicable`.
* Charge catalogue: `create table kb_extra_charges(extra_charges_id PK, extra_charges_name varchar(1024),
  sac_code varchar(32), tax_enabled integer, enabled integer, tax_id → kb_tax_code)` seeded with
  `1 Shipping`, `2 Packaging`, `3 Adjustment`. **That is where "packaging" lives** — it is an additional
  charge, not a packaging unit; the only other "pack"-ish storage is the unit-mapping
  (`base_unit → secondary_unit × conversion_rate`) used for loose/carton quantities.
* Screens/models: `Lin/android/vyapar/transaction/ui/AdditionalChargeForTxnActivity;`,
  `Lin/android/vyapar/transaction/ui/models/AdditionalChargeForTxn;` — a `Parcelable` of
  `{AcName name, String ?, TaxCode tax, boolean, boolean, List}`, plus
  `Lin/android/vyapar/settings/ui/models/AcName;` = `{String name, FieldValidation}`;
  KMP `AdditionalChargeDbModel(id=…)`; layouts `res/layout/ac_for_txn_layout.xml`,
  `ac_setting_for_txn_layout.xml`; strings `ADDITIONAL_CHARGES_ON_SALE`, `ADDITIONAL_CHARGES_ON_PURCHASE`,
  `add_tax_on_additional_charges`, `transaction_charges_label`, `bg_bs_da_charges`.

---

## 8. Serial / batch / expiry / size / MRP tracking

* Stock-tracking rows: `create table kb_item_stock_tracking(ist_id PK, ist_batch_number varchar(30),
  ist_serial_number varchar(30), ist_mrp double, ist_expiry_date datetime, ist_manufacturing_date datetime,
  ist_size varchar(100), ist_item_id → kb_items, ist_current_quantity double, ist_opening_quantity double,
  ist_type integer default 0)`; item master flag `item_ist_type` (SQL uses `=0/1/2`: none/batch/serial),
  line flags `lineitem_batch_number/expiry_date/manufacturing_date/size/mrp` + `lineitem_ist_id`.
* Java BizLogic wrappers (all `Parcelable`):
  `in.android.vyapar.BizLogic.ItemStockTracking` — fields `a..Y, Z, s, q, M, Q, H, i, d, e, f, g, b, c`
  and writes `ContentValues` with keys `ist_batch_number, ist_serial_number, ist_current_quantity,
  ist_item_id, ist_mrp, ist_expiry_date, ist_manufacturing_date, ist_size`;
  `in.android.vyapar.BizLogic.SerialTracking` — `(int b, int d, long a, String c, boolean e)` plus a
  static `Set<Integer>` of txn types **{1, 21, 23, 24, 30}** = the outward vouchers that consume serials
  (Sale, Credit Note, Debit Note, Sale Order, Delivery Challan);
  `in.android.vyapar.BizLogic.ItemUnit`, `TaxCode` (`sen` = id, name, rate, 2 ints, `Map<Integer,Double>`
  of multi-tax slabs), `ExpenseCategoryObject` `(int, String, double, int)`.
* Serial ledger: `create table kb_serial_details(serial_id PK, serial_item_id → kb_items,
  serial_number varchar(256), serial_current_quantity double)` and
  `create table kb_serial_mapping(serial_mapping_id PK, serial_mapping_serial_id → kb_serial_details,
  serial_mapping_lineitem_id → kb_lineitems, serial_mapping_adj_id → kb_item_adjustments)` —
  i.e. serial ↔ (line item | stock adjustment) with writer keys
  `serial_mapping_lineitem_id`, `serial_mapping_serial_id`.
* UI: `activities/SerialNumberActivity`, `ItemStockTrackingReportActivity`,
  `ist/models/IstDataModel` (+ `$Batch`, `$Serial`), `BatchRowUi(id=`, `ExpiredItemData(itemName=`,
  `error_select_serials_to_return`, `lineItemSerialList` / `lineItemReturnedSerialList` on the line model,
  e-invoice `EInvoiceDTO.Item.BatchDetails`.
* Expiry/batch reports + `adjustment_ist_mapping_id`, `kb_adjustment_ist_mapping` (stock adjustment ↔ ist).

---

## 9. Units, unit mappings, quantity handling

* `create table kb_item_units(unit_id PK, unit_name varchar(50) unique, unit_short_name varchar(10) unique,
  unit_full_name_editable integer default 1, unit_deletable integer default 1)`.
* `create table kb_item_units_mapping(unit_mapping_id PK, base_unit_id → kb_item_units,
  secondary_unit_id → kb_item_units, conversion_rate double)` — one base + one secondary unit per
  mapping, item master holds `base_unit_id`, `secondary_unit_id`, `unit_mapping_id`;
  line items store the *used* pair (`lineitem_unit_id`, `lineitem_unit_mapping_id`).
  Deleting a mapping is guarded: `Select count(*) from kb_lineitems where lineitem_unit_mapping_id = …`.
* Screens: `Lin/android/vyapar/AddItemUnitMappingActivity;` (2 spinners + conversion-rate field +
  `HashMap` caches + RecyclerView of existing mappings), `AdditionalItemColumnsActivity`,
  KMP `vyapar.shared.legacy.item.bizLogic.{ItemUnit,ItemUnitMapping}`,
  models `ExpandedUnitCardModel(itemUnitMapping=`, `EditMappingModel(itemUnitMapping=`,
  `ExpandableItemUnit(itemUnit=`, `base_unit_id`, `line_item_unit_mapping_id`.
* Unit vocabulary seeds in dex: `CUBIC CENTIMETERS`, `CUBIC METER`, `BILLION_OF_UNITS`, etc.

---

## 10. Expense / Other-income / Journal-Contra family

* `TXN_TYPE_EXPENSE (7)` and `TXN_TYPE_OTHER_INCOME (29)` are item-less vouchers: no `kb_lineitems`
  rows; instead `txn_category_id → kb_names(name_id)` + `txn_party_expense_type`,
  amount split exactly like invoices (`txn_cash_amount` + `txn_balance_amount`).
  Evidence: `select name_id, sum(txn_cash_amount + txn_balance_amount) from kb_names left join
  kb_transactions on name_id=txn_category_id where txn_type=7 …`.
* Category screens/models: `expense/ExpenseActivity`, `expense/categories/ExpenseCategory`,
  `expense/categories/ExpenseCategoriesFragment`, `expense/items/ExpenseItem`,
  `ExpenseOrOtherIncomeCategoryListActivity`, `BizLogic/ExpenseCategoryObject`,
  reports `ExpenseorOtherIncomeCategoryReport`, `ExpenseorOtherIncomeItemReport`, `OtherIncomeReport`,
  `EXPENSE_TYPE`, `EXPENSE_TYPE_FILTER_LIST`, `DIRECT_EXPENSE`/`INDIRECT_EXPENSE`,
  `OTHER_INCOMES_DIRECT`/`OTHER_INCOMES_INDIRECT`, `OTHER_ACCOUNT_EXPENSE_ID`,
  settings `SETTING_OTHER_INCOME_ENABLED`, pref `LAST_SAVED_PAYMENT_TYPE_EXPENSE`.
* Journal/Contra (new KMP accounting): `create table journal_entry(id, journal_firm_id → kb_firms,
  reference_number, date, description, created_by/modified_by → urp_users, created_at, modified_at)`;
  `create table journal_entry_line_items(id, journal_entry_id → journal_entry, account_id →
  chart_of_accounts_mapping, amount double, amount_type INTEGER, reference_account_id →
  chart_of_accounts_mapping, txn_link_id → kb_transactions(txn_id))` — double-entry lines with a
  DEBIT/CREDIT `amount_type` (`CREDIT`, `CREDIT_NOTE`, `DEBIT`, `DEBIT_NOTE`, `SUNDRY_CREDITOR`,
  `SUNDRY_DEBITOR` in strings) and an optional FK back to a voucher.
  `create table other_accounts(id, name, opening_balance, opening_balance_date, account_type,
  account_identifier default 0, …)` + `chart_of_accounts_mapping(id)`; txn types 81/82
  (`journal_entry_received`/`journal_entry_paid`) surface journal entries inside the voucher list;
  `JOURNAL_ENTRIES`, `JOURNAL_ENTRY_PAID`, `TXN_TYPE_JOURNAL_ENTRY_*`.
* Money-transfer family: `MoneyInTransaction`/`MoneyOutTransaction` (3/4),
  `P2PPaid/P2PReceivedTransaction` (50/51), `BankAdjustmentTxn` (`kb_bank_adjustments(bank_adj_*)`),
  `CashAdjustment` (`kb_cash_adjustments(cash_adj_*)`), `cashInHand/*`,
  `LoanTransaction`/`LoanExpenseActivity` (`T11_LOAN_ACCOUNT_TXN`, `LOAN_TXN_TYPE`, `IS_LOAN_EXPENSE`),
  `StockTransferTxn` + `reports/stockTransfer`, `ItemAdjustmentTxn` (`kb_item_adjustments`,
  `item_adj_type = 10` = manufacturing), `FixedAssetDetailTxn` (60/61, `EXPENSE_ON_PURCHASE_OF_ASSETS`),
  `LoyaltyPartyTransactionsActivity` (67/68/90).

---

## 11. Screen/component inventory relevant to vouchers (manifest + dex)

Registered in `dex/manifest.txt`: `NewTransactionActivity` (entry, parent `home.HomeActivity`),
`ViewOrEditTransactionDetailActivity` (parent `newDesign.partyDetails.PartyDetailsActivity`),
`SelectTransactionActivity`, `EstimateDetailsActivity` (handles both `estimate_detail_view` and
`proforma_invoice_detail_view` modes), `DeliveryChallanActivity`, `DeliveryChallanConversionActivity`
(`lineItemList`, `extra_serial_number`, `btnNext/btnCancel`, `cbNoConversionGoodsReturn` style dialog),
`ExpenseActivity`, `ExpenseOrOtherIncomeCategoryListActivity`, `PaymentReminderActivity`,
`PaymentTermActivity`, `AddItemUnitMappingActivity`, `DeleteAuthenticationActivity`,
`TransactionSettingsActivity`, `TransactionSmsActivity`, `TransactionThemeChooserActivity`,
`PrintTxnItemTableSettingActivity`, `CustomMessageSelectTxnActivity`, `EditTxnMessageActivity`,
`activities/TxnListActivity`, `activities/TxnPdfActivity`, `orderdetail/OrderDetailActivity`,
`newDesign/transactionLisitng/TransactionListingFragment`,
`paymentgateway/utils/GenerateTransactionPaymentLinkWorker`, `PaymentWebsiteActivity`.
Named methods surviving in `NewTransactionActivity` show the entry contract:
`addNewLineItemRow(View)`, `expandItemDetail(View)`, `setSubtotalAmountandQtyAmount(View)`,
`saveAndCloseTransaction(View)`, `onBackPressed()`, and
`P3(xy1, boolean, in.android.vyapar.GsonModels.SaleType)` — `SaleType` = `NONE(-1) | CASH(1) | CREDIT(0)`
(cash vs credit sale), used as an explicit parameter of the save path.

Message/notification config: `create table kb_txn_message_config(txn_type, txn_field_id,
txn_field_name varchar(50), txn_field_value varchar(256), PK(txn_type, txn_field_id))`
(migration `set txn_field_name='Invoice Amount' where txn_field_name='Amount' and txn_type=1`) —
per-voucher-type configurable SMS/WhatsApp template fields, with `GsonModels/SaleTxnWhatsAppMsg`,
`TxnSMSPOJO/TxnSMSRequest`, `vyapar.shared.legacy.transaction.messages.*`, `EditTxnMessageActivity`.

Attachments: `create table transaction_attachments(id, txn_id NOT NULL → kb_transactions ON DELETE
CASCADE, uuid UNIQUE, name)` and `attachments(id, url, name)`; header field `attachmentList`,
intent key `transactionAttachment`.

---

## 12. Minimal clone contract (what to implement)

1. `txn_type` integer registry exactly as section 1; voucher header + `txn_sub_type`.
2. Header: party FK + category FK into one `names` table; amounts decomposed into
   `cash_amount / balance_amount / loyalty_amount` + `current_balance`; tax/discount/round-off;
   `status` + `payment_status`; `due_date` + `payment_term_id`; prefix per type; e-invoice/e-way/IRN block;
   firm/store/user/currency scoping; UDF list; attachments; TNC id.
3. Line item: qty + free qty + rate + net total, line tax/discount (+type), unit + unit-mapping ids,
   MRP/batch/expiry/manufacturing/size/serial (+ `ist_id` FK and `is_serialized`), ITC,
   additional cess, `tax_basis`, `total_amount_edited`, order back-references.
4. Payments: 1 header mode + N `txn_payment_mapping` rows (mode, amount, reference, cheque/EDC refs);
   cheque-status sub-state; link table for settlement between vouchers.
5. Conversions: `linked_transactions(source → destination)` graph, `is_converted` exposure,
   convert events for Estimate/Proforma/SaleOrder/DeliveryChallan; partial convert via `txn_links.amount`.
6. Tracking: `serial_details` + `serial_mapping(lineitem|adjustment)`; `item_stock_tracking` rows keyed
   by (item, batch, serial, mrp, size, expiry, mfg) with current/opening qty and `ist_type`.
7. Units: units dictionary + `units_mapping(base, secondary, conversion_rate)` + per-line unit choice.
8. Discounts at 3 levels with percent|amount flag each.
9. Additional charges fixed at 3 slots with SAC code + tax + ITC, catalogue
   {Shipping, Packaging, Adjustment}.
10. Soft delete via `recycle_bin(txn_data_json)` + restore, and a `transaction_attributes` 1:1
    compliance table.
