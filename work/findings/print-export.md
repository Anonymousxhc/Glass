# print-export

Target: Vyapar `in.android.vyapar` v31.0.1 (vc728), base.apk + 7 dex.
Scope: every way the app produces an **output artifact** — printed invoice, saved/shared PDF,
Excel export, image, share link, hosted storefront — plus what it *imports* to feed those.

Client-vs-server is labelled per finding. All class names below are what the dex actually
contains (`in.android.vyapar.*` keep readable names; the print pipeline itself is R8-obfuscated
into 2–3 letter `defpackage` names, which I give verbatim).

---

## 0. Layer correction that matters for this dimension

The Dart/Flutter half of the APK is **not** the billing UI. `work/dart_out/scripts.jsonl` resolves
every first-party Dart path to `package:adkrity_app/main-module/...` (an ad-creative marketplace
SDK; build host `file:///Volumes/Dev/projects/vyapar_app_sdk/...`). The Dart package inventory has
`share_plus`, `saver_gallery`, `file_picker`, `webview_flutter`, `sqflite`, `upi_pay`,
`razorpay_flutter` — and **no `pdf`/`printing`/`esc_pos_bluetooth` package at all**.

Consequence: 100% of print/export artefact production lives in the **Java/dex business layer**
(+ a small KMP slice for scheduled reports). `strings -n 6 classes*.dex | grep` and `droidasc`
are the right tools for this dimension; `dart_out` is nearly useless here except to *prove* the
negative (no Flutter PDF generation).

---

## 1. Invoice printing — settings surface (client)

Registered activities (from `work/dex/manifest.txt`), with `parentActivityName` chaining that
reveals the IA:

| Activity | Role |
|---|---|
| `settings.activities.InvoicePrintSettingsActivity` | host; `k0()` installs fragment `z3c`; reads extra `"Source of setting"`; role-gated by `j9k.INVOICE_PRINT_SETTINGS` / `"action_view"`; falls through to upgrade bottom sheet `vaa` when not permitted |
| `PrintTxnItemTableSettingActivity` | per-column item-table config (child of InvoicePrintSettings) |
| `settings.activities.ResizeItemTableActivity` | per-column **width** editor; `getParcelableArrayListExtra("item_table_header_list")` → fragment `u8k`; `screenOrientation=0` (sensor, because it shows a wide table) |
| `settings.activities.ThermalPrinterAdditionalItemDetailsActivity` | extra item fields on thermal receipts → fragment `iwn` |
| `settings.activities.OriginalDuplicateSettingActivity` | ORIGINAL / DUPLICATE / TRIPLICATE watermark text (Compose host: `bb`/`iai` view-tree owner) |
| `CustomHeaderSettingActivity` | user-renamed column headers (`VYAPAR.ADDITIONALCESSCOLUMNHEADERVALUE`, `DISCOUNTCOLUMNHEADERVALUE`, `HSNCODECOLUMNHEADERVALUE`, `FINALITEMPRICECOLUMNHEADERVALUE`, `BATCHNOCOLUMNRATIOVALUE`) |
| `themechooseractivity.TransactionThemeChooserActivity` | theme picker (child of InvoicePrintSettings) |
| `thermalprint.ui.ThermalPrinterActivity` | device picker + print runner |
| `thermalprint.ui.addwifiprinter.AddWifiThermalPrinterActivity` | Wi-Fi printer entry (Compose) |
| `thermalprint.ui.BuyThermalPrintersWebViewActivity` | hardware store webview |
| `printerstore.activity.PrinterStoreActivity` | printer store |
| `activities.TxnPdfActivity` | invoice preview (WebView) + signature/logo capture |
| `ShareUtilsActivity` | single funnel for every share action |

`ItemTableHeaderDm` (`settings/datamodels/`) is the parcel passed between the two table screens:
`{a:String headerLabel, b:double widthRatio, c:String settingKey (default "VYAPAR.ITEMCUSTOMFIELDS"),
d:boolean enabled, e:int position}`. That is the whole data model of "customise & resize the item
table". `PrintTxnItemTableSettingActivity` reads per-column value keys `VYAPAR.ITEMCOUNTVALUE`,
`ITEMBATCHNUMBERVALUE`, `ITEMEXPIRYDATEVALUE`, `ITEMMANUFACTURINGDATEVALUE`, `ITEMMRPVALUE`,
`ITEMSIZEVALUE`.

### 1.1 The setting keys themselves (client, SharedPreferences)

Two parallel namespaces exist: `VYAPAR.*` = the *stored* key, `SETTING_*` = the *enum* used by the
settings-search / CleverTap push machinery (`workmanager/PushSettingsToClevertapWorker`,
`analytics/SettingsSearchDumpData`, endpoint `api/ns/docdb/settingsearch`). Output-relevant set:

```
VYAPAR.PRINTPAGESIZE          SETTING_PRINT_PAGE_SIZE       (A4 / A5 — res print_page_size_A4_setting / _A5_setting)
VYAPAR.PRINTORIENTATION       SETTING_EXPAND_TABLE_TO_PRINT_ON_WHOLE_PAGE
VYAPAR.PRINTTEXTSIZE          SETTING_PRINT_TEXT_SIZE
VYAPAR.PRINTCOMPANYNAMETEXTSIZE  SETTING_PRINT_COMPANY_NAME_TEXT_SIZE
VYAPAR.EXTRASPACEONTXNPDF     SETTING_EXTRA_SPACE_ON_TXN_PDF
VYAPAR.MINITEMROWS…           SETTING_MIN_ITEM_ROWS_ON_TXN_PDF
VYAPAR.PRINTPAGENUMBERS       SETTING_PRINT_PAGE_SIZE / print_page_numbers
VYAPAR.PRINTREPEATHEADERINALLPAGES
VYAPAR.PRINTCOPYNUMBER        SETTING_PRINT_COPY_NUMBER
VYAPAR.PRINTORIGINALCOPYTEXTENABLED / PRINTDUPLICATECOPYTEXTENABLED / PRINTTRIPLICATECOPYTEXTENABLED
VYAPAR.PRINTLOGOONTXNPDF      SETTING_PRINT_LOGO_ON_TXN_PDF
VYAPAR.PRINTCOMPANYNAMEONTXNPDF / PRINTCOMPANYADDRESSONTXNPDF / PRINTCOMPANYEMAILONPDF / PRINTCOMPANYNUMBERONTXNPDF
VYAPAR.PRINTDESCRIPTIONONTXNPDF  SETTING_PRINT_DESCRIPTION_ON_TXN_PDF
VYAPAR.PRINTITEMQUANTITYTOTALONTXNPDF
VYAPAR.PRINTTXNTIMEONINVOICEENABLED, PRINTSINNUMBERENABLED, PRINTBATCHNOENABLED, PRINTDCNOSONOENABLED
VYAPAR.PRINTITEM{CODE,COUNT,DESCRIPTION,DISCOUNTAMOUNT,DISCOUNTPERCENTAGE,EXPIRYDATE,HEADERSORDER,
  MANUFACTURINGDATE,MRP,PRICE,QUANTITY,SERIALNUMBER,SIZE,TAXAMOUNT,TAXPERCENT,TOTALAMOUNT,UNIT}ENABLED
VYAPAR.PRINTHSNCODEENABLED / PRINTTAXABLEAMOUNTENABLED / PRINTFINALITEMPRICEENABLED
VYAPAR.PRINTBILLOFSUPPLYFORNONTAXTXN   SETTING_PRINT_BILL_OF_SUPPLY_FOR_NON_TAX_TXN
VYAPAR.PRINTGULFCOUNTRYQRCODE          SETTING_PRINT_GULF_COUNTRY_QR_CODE
VYAPAR.PRINTTRANSPORTATIONDETAILS      SETTING_PRINT_PAYMENT_MODE, SETTING_PRINT_TINNUMBER
VYAPAR.PRINTACKNOWLEDGMENT             SETTING_PRINT_ACKNOWLEDGEMENT
VYAPAR.PRINTAMOUNTGROUPING / PRINTAMOUNTSINDELIVERYCHALLAN / PRINTAMOUNTTILLSPECIFIEDPLACES
VYAPAR.PRINTYOUSAVEENABLED   VYAPAR.CUSTOMERSIGNATUREENABLED / SETTINGCUSTOMERSIGNATURETEXT / SETTINGSIGNATURETEXT
VYAPAR.TXNPDFTHEME, TXNPDFTHEMECOLOR, TXNPDFDOUBLETHEMECOLOR, TXNTHERMALTHEME
VYAPAR.DEFALUTPRINTER (sic)  VYAPAR.DEFAULTTHERMALPRINTERADDRESS
VYAPAR.INVOICESHAREASIMAGE   VYAPAR.ENABLEOPENDRAWERCOMMAND  VYAPAR.SETTINGENABLEAUTOCUTPAPER
VYAPAR.THERMALPRINTER{PAGESIZE,TEXTSIZE,COPYCOUNT,EXTRAFOOTERLINES,NATIVELANG,CUSTOMIZECHARACTERCOUNT}
VYAPAR.USEESCPOSCODESINTHERMALPRINTER
VYAPAR.AMOUNTINWORDFORMAT, AMOUNTDECIMALNUMBER, CURRENTDATEFORMAT, CURRENCYSYMBOL
```

### 1.2 Settings-search label taxonomy = the real feature list

`strings … | grep -oE '"Print_[A-Za-z0-9_]+'` yields the exact option inventory the UI exposes,
split by printer kind. This is the cheapest faithful spec to clone from:

* `Print_regular_*` (A4/A5): `paper_size`, `orientation`, `text_size`, `company_name_size`,
  `current_theme`, `current_theme_color`, `theme_color`, `extra_space_on_top_of_pdf`,
  `print_repeat_header_in_all_pages`, `original`/`print_original`, `min_no`, `s_no`, `serial_no`,
  `batch_no`, `mfg_date`, `exp_date`, `mrp`, `size`, `model_no`, `hsn`, `units`, `item_description`,
  `signature_text`, `print_customer_signature_text`, `payment_mode`, `delivered_by_in_delivery_challan`,
  `received_by_in_delivery_challan`, `make_regular_printer_default`, plus a full
  `Print_regular_item_table_setting_<col>` / `…_<col>_header` pair for every column
  (count, description, discount, discount_percentage, discount_header, final_price_per_unit,
  price_per_unit, hsn, item_code, item_name_header, batch_number, exp_date, mfg_date, model_number,
  mrp, serial_number, size, sl_number, tax_amount, tax_percentage, taxable_ad_cess_header,
  taxable_amount_header, taxable_price_per_unit, total_amount, units_of_measurement — each with an
  optional `_header` rename entry).
* `Print_thermal_*`: `size`, `hsn`, `batch_no`, `exp_date`, `mfg_date`, `mrp`, `model_no`, `s_no`,
  `serial_no`, `size`, `units`, `item_description`.
* Plain `Print_*`: `address`, `amount_in_words`, `balance_amount`, `bill_of_supply_for_non_tax_invoices`,
  `company_logo`, `company_name`, `current_balance_of_party`, `email`, `gstin_on_sale`, `phone_number`,
  `print_amount_with_grouping`, `print_description`, `received_amount`, `tax_details`,
  `print_signature_text`, `on_whole_page`, `transaction_name_credit_note`, `Print_pdf_report`.

### 1.3 Themes (client)

Enum `p4c` (22 constants): `THEME_1..THEME_13`, `THERMAL_THEME_1..5`, `MOBILE_FRIENDLY_THEME`,
`THEME_TALLY`, `LANDSCAPE_THEME_1/2`, each carrying a `gvn action` (themeId + isThermal flag).
Server-free name maps:

```
p4c.getRegularPrinterThemeName(id 1..16):
  1..4 "Theme 1..4"; 5 "GST theme 4"; 6 "GST theme 5"; 7 "GST theme 2"; 8 "GST theme 6";
  9 "GST theme 3"; 10 "GST theme 1"; 11 "Double Divine"; 12 "French Elite"; 13 "Arabic Theme";
  14 "Tally Theme"; 15/16 "Landscape Theme 1/2"   (else throws "there is no pdf theme of id: ")
p4c.getThermalPrinterThemeName(id 0..4) -> "THERMAL_THEME_1..5" ("there is no thermal theme of id: ")
```
Colour palette: `THEME_COLOR_1..24` / resource `invoice_theme_color_1..24`; `THEME_A5_1`, `THEME_A5_2`.
Monetisation gate: enum `f08.PAID_INVOICE_THEMES` + `isResourceNotAccessibleBlocking()` → upgrade
bottom sheet (`kz7.H(...)`); `TxnPdfActivity.F0()` is one caller. Analytics events
`Invoice_theme_changed`, `Invoice_theme_explored`, `From_invoice_theme`, `To_invoice_theme`.

### 1.4 Preview screen (client)

`activities.TxnPdfActivity` inflates `activity_invoice_pdf`, hosts a WebView
(`fc` binding; `V0.l1.setUserInputEnabled(false)`) plus an inner class
`TxnPdfActivity$JavaScriptInterface` with `@JavascriptInterface`-style methods
`onClickLogo / onClickSignature / onClickBusinessDetails / onClickTermsAndCondition /
onClickBranding` — i.e. the *preview itself is clickable HTML* that jumps back into native
settings. Signature capture: `signature_dialog_layout` + `in.android.vyapar.util.SignatureView`,
or camera/gallery → `com.yalantis.ucrop` (`com.yalantis.ucrop.OutputUri`, `ERROR_UCROP`) → 300×300
bitmap. It also collects UPI/payment KYC entry points (`upiBtn`, `collectBtn`, `failedBtn`).
Print is dispatched by `veo.B(txnId, activity, themeId, singleColor, doubleColor, …)`; share by
`veo.H(…)`.

---

## 2. Invoice printing — generation pipeline (client)

Log-tagged use case (obfuscated `ms9`/`js9`/`gs9`/`hs9`/`es9`, all self-labelled
`"GetFileForTransactionUsecase: …"`) with an exactly ordered pipeline:

1. `es9`: `fetching invoice expansion script` → `expansion script fetched, present=` (a per-firm
   HTML/JS "expansion" blob; **server-fetched**, cached).
2. `hs9` → HTML string. `js9` logs `html generated, chars=…, useTemplateForImage=…, txnCount=…`
   (`useTemplateForImage` = the "share invoice as image" toggle; single-txn ≤5 items ⇒ image path).
3. `gs9` loads the HTML into an off-screen `WebView`, logs `webview loaded, starting pdf write`.
4. `ms9.a(WebView, isA5, "openPDF", outPath, …)`:
   ```java
   new PrintAttributes.Builder()
       .setMediaSize(isA5 && pageSetting==2 ? ISO_A5 : ISO_A4)
       .setResolution(new PrintAttributes.Resolution("zooey","PRINT_SERVICE",300,300))
       .setMinMargins(NO_MARGINS).setColorMode(1 /*color*/);
   PrintDocumentAdapter a = webView.createPrintDocumentAdapter("tempPDF");
   dta.S(a, outPath, shouldPrintPageNumbers, copyCount, cb);
   ```
   `ms9` also logs `createPdf shouldPrintPageNumbers=…, copyCount=…` where copyCount is bumped by
   "print original/duplicate/triplicate" flags.
5. `dta.S(…)` = the file writer: creates a row in
   `MediaStore.Files.getContentUri("external")` with `_display_name`, `mime_type`,
   `relative_path = Documents/<subdir>` (mime sniffed from extension: `.xls →
   application/vnd.ms-excel`, `.xlsx → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
   `.pdf → application/pdf`, fallback `application/*`), opens a `ParcelFileDescriptor`, then drives
   `adapter.onLayout(...)` / `onWrite(...)` (`"PdfPrint: calling onLayout"`,
   `"PdfPrint: success, file bytes="`).

**No iText/OpenPDF/Pdfium/flutter-pdf.** PDF is produced entirely by the Android WebView print
adapter (`classes*.dex` contains no `itext`/`lowagie`/`pdfium` symbol; only
`com.tom_roush.pdfbox` = the AndroidPdfViewer *reader*, used for viewing, not writing).
Real printing to a physical printer goes through `android.print.PrintManager`; two
`PrintDocumentAdapter` subclasses exist that wrap a WebView adapter — `zfh`
(`onStart(){ this.a = webView.createPrintDocumentAdapter(name) }`, `onFinish(){ webView.destroy() }`)
and `bs9`.

### 2.1 HTML is string-built, not templated

The invoice body is `StringBuilder` HTML in `uy0` (a class implementing
`PrintDocumentAdapter`-ish interfaces): `T()` emits fixed-percentage table cells
(`width=11.6%`, `46.4%`, `8%`, `34%`), `W()/X()` dashed cut-lines, `S()/R()` amount rows,
`J()` QR generation (`x4j` size=200 colour `#343434`) → `D(Bitmap)` → `<img src='data:image/png;base64,…'
class='qrCode'>` plus "Scan this code to pay". `uy0.y()` / `uy0.N()` wrap the body in a **branding
footer** (`www.vyaparapp.in`, `https://billing.vyaparapp.in/invoice-billing-pdf`, base64
`branding_image_for_invoice`, and a `<div onclick='onClickRemoveBranding()'
class='invoicePreviewRemoveBranding'>` for paid-plan upsell). Free-vs-paid branding is
client-decided from licence flags (`q75.c()`, `ksi.d()`).
Report HTML is likewise string-built (`qd8.B()` → `<tr style="background-color: lightgrey">…`).
Only *preview thumbnails* are asset HTML.

### 2.2 Asset HTML templates (client, in APK)

```
assets/theme5_sample.html … theme13_sample.html
assets/themeA5_1_sample.html  themeA5_2_sample.html  themeTally_sample.html
assets/theme_{7,9,10}_bank_and_signature_section_sample.html (+ _gulf.html variants)
assets/fonts/quicksand.ttf            (referenced as "ThermalFont")
assets/icon_ttf/vyapar_icons.ttf
assets/images/invoice_logo_sample.png
```
plus `file:///android_asset/` base URL and an inline A5 hint
`<style> @page {size: A5 landscape;}`. The thermal stylesheet, verbatim from `izn.h`:
`@font-face{font-family:ThermalFont;src:url("file:///android_asset/fonts/quicksand.ttf")}
body{margin:0;padding:0;word-break:break-all;font-family:'ThermalFont';letter-spacing:…px;
line-height:115%}td{vertical-align:top;padding:…px}` — i.e. thermal receipts are also HTML,
laid out by a block renderer chain (`bij`/`iij`/`mij`/`yij`/`fij`).

---

## 3. Thermal printing (client, mostly; one server-assisted path)

`thermalprint.ui.ThermalPrinterActivity` — Compose host (`sma`), view-model `oyn(xrk,jxn,lnq)`,
launch mode enum `ThermalPrinterActivity$a{DEFAULT_PRINTER_SELECTION, PRINTING}` passed as extra
`launch_mode` together with `txn_id` and `thermal_theme_id` (see `veo.E`).

Connection transports (open-source ESC/POS connection abstraction, kept in
`in.android.vyapar.thermalprint.library.*` namespace for its exception only):

| Transport | Class | Detail |
|---|---|---|
| Bluetooth | `k82` | `BluetoothDevice.createRfcommSocketToServiceRecord(UUID "00001101-0000-1000-8000-00805f9b34fb")` (SPP); `"Unable to connect to bluetooth device."` |
| Wi-Fi / TCP | `phn(ip,port)` | `Socket.connect(InetSocketAddress, 1000ms)`, recursive retry up to 3, `"TcpConnection, unable to connect, retrying: n"`, `"Unable to connect to TCP device."` |
| USB | `alp(UsbManager,UsbDevice)` | custom `OutputStream` `crf`; `"Unable to connect to USB device."` |

Base class `xa6` holds `OutputStream a` + `byte[] b` buffer and `write(byte[])` does
array-concat. Type enum `zxn{Bluetooth("bluetooth"),Usb("usb"),Wifi("wifi")}`.
Wi-Fi printers are persisted as `thermalprint.models.ThermalPrinterWifiData{int id, String name,
String ip ("ip"), String port ("port")}` (Parcelable, JSON keys `id/name/ip/port`); the list lives
in prefs under `thermal_printer_wifi_devices_index` / `getThermalPrinterWifiDevices()`;
`VYAPAR.DEFAULTTHERMALPRINTERADDRESS` is the chosen default.

Permission/location dance (client): `USE_ESCPOS`/Bluetooth needs **location** on recent Android —
`ThermalPrinterActivity.K0()` builds a `LocationRequest`, checks `LocationSettingsRequest` and
logs `Thermal Printer WebView: Requesting Location Permission / Location settings enabled / not
resolvable`; strings `s_require_permission_location_for_thermal_printer`,
`s_require_permission_nearby_devices_for_thermal_printer`. Bluetooth flow: `REQUEST_ENABLE`,
`startDiscovery()`, `ACTION_FOUND`/`DISCOVERY_FINISHED` receiver (`l82`), bonded devices (`k82` list).

ESC/POS command emission (`ovo`, plus constant pool `gr5`):
* text style `ESC ! n` = `{27,33,n}` with n chosen by `ovo.h(size)` (7 = font A+double-height
  logic, 8, 16, 32, 1, -128 for bold/invert) keyed off `xvn{SMALL,MEDIUM,LARGE}`
  (`sizeId 1/2/3`, `htmlSize` floats ≈9.0/10.5/11.0f);
* text is encoded `ISO_8859_1`;
* alignment/width via `ovo.e(padChar,width,align,text)` and `ovo.f(width,align,text)` where
  align 1=left 2=right 3=centre, width = character count per line — this is exactly what
  `VYAPAR.THERMALPRINTERCUSTOMIZECHARACTERCOUNT` + `SETTING_THERMAL_PRINTER_PAGE_SIZE` +
  `ReceiptEscPosMeasureConstraints(widthChars=…, …)` (`eij`) control;
* QR code via `GS ( k`: bytes `29,40,107, pL, pH, 49('1'), 80('P'), 48('0')` then payload, then
  `gr5.Y`/`gr5.Z` (store/print) — `ovo.v(payload)` and `ovo.u(payload+"Scan this QR code to pay")`;
* `ovo.m(...)`/`l(...)` for label:value rows, `ovo.a(StringBuilder, z9g)` for bank details
  (`"Bank Name: "`), `ovo.r()`/`p()` line feeds;
* long-lived socket cache: `"Using cached active connection for thermal printing."`.
* Feature gates: `VYAPAR.USEESCPOSCODESINTHERMALPRINTER` (raw ESC/POS vs HTML-image path),
  `VYAPAR.THERMALPRINTERNATIVELANG` (`thermal_enable_native_lang_print`,
  `Enable native language in thermal printer` — non-ASCII via code pages),
  `VYAPAR.ENABLEOPENDRAWERCOMMAND` (kick cash drawer), `SETTING_ENABLE_AUTOCUT_PAPER`.
* Failure path: `ERROR_PRINTING_USING_THERMAL_PRINTER`, `PrintIssueBottomSheet`,
  `no_default_thermal_printer_selected`, `default_printer_error`,
  `s_error_default_printer_not_paired / _removed`.

White-label exception (client): if the build flavour is `ctp.AlankitApnaPay`, `veo.E` skips
Vyapar's own stack and fires an explicit `Intent(ACTION_MAIN)` to component
**`com.apnapay.szzt/com.apnapay.szzt.PrintActivity`** carrying package name + a JSON payload
(`ovo.c(Activity, JSONObject)`).

Monetisation/hardware funnel (mostly server): `BuyThermalPrintersWebViewActivity` loads
`/web/thermal-printers-store` and `/web/vyapar-hardware/thermal_printer`;
`PrinterStoreActivity`; `buy_thermal_printer_banner_{title,subtitle,layout,first_shown_date,
shown_in_home_screen}` shown in home / invoice-print screen / default-printer screen
(`show_buy_thermal_printer_banner_in_*` keys); `ThermalPrinterOrderDeliveryStatusResponse(message=…)`,
`ThermalPrinterOnboardingAlert(orderId=…)` ⇒ order placement/tracking is server-side.

---

## 4. Excel export / import — Apache POI (client)

POI **is** bundled (classes4/5 dex: `org.apache.poi.{hssf,xssf,ss,poifs,xwpf}` + xmlbeans + the
`schemasMicrosoft*`/`org.openxmlformats` generated bindings). But ref-analysis shows the *app*
only touches **HSSF**:

* `findrefs type XWPFDocument` → 4 hits, **all inside `org/apache/poi`** ⇒ **no DOCX feature**;
  the word-processing half of POI is dead weight.
* `findrefs type XSSFSheet` → only POI-internal hits ⇒ app never *writes* xlsx.
* `findrefs type HSSFSheet` → POI internals **plus** app classes `pl1` (`a,b,h,i`) and `sl1.e()`.

Verified users:

| Where | What |
|---|---|
| `ExportItemsActivity` | `.xls` item export: `Environment.getExternalStorageDirectory()` + `SimpleDateFormat("dd_mmm_yyyy")` → `exported_items_<date>.xls` (collision-suffixed `…(2).xls`), renamable via AlertDialog; `C0(Collection, HSSFSheet, HSSFCellStyle, HSSFPalette, HSSFFont)` styles header row (`palette.setColorAtIndex(12,84,-116,-39)`, bold white font, wrap, centre) then writes rows; success toast `export_item_sd_write_success`; needs `WRITE_EXTERNAL_STORAGE` (`writePermissionForExportItemsToSD`) |
| `ImportItemFileChooserActivity` / `ImportMsExcelItemActivity` | **generates** the blank sample file `sample_import_item_<epochMillis>.xls` with `HSSFWorkbook` so the user can fill it in; column set driven by `COL_LABEL_*` string resources (`item code, item name, hsn code, sale price, purchase price, disc type, disc abs value, opening stock qty, min stock qty, item location, tax rate, incl tax, default mrp, online store price`); import file filter regex `.*[.]((xls)|(xlsx))$` (also `tmb.b`) |
| `pl1` | report → workbook, e.g. `HSSFWorkbook().createSheet("VAT 201 Return Report")` |
| `sl1.e()` | builds `HSSFWorkbook` for a report (multi-block `createRow/createCell`) |
| POI `WorkbookFactory`/`XSSFExcelExtractor` | tolerant reader for user-supplied `.xls`/`.xlsx` |

Party import mirrors item import: `ImportPartyFileChooserActivity` (same `xls|xlsx` regex),
`PartyImportConfirmationActivity`, error strings `Party is duplicated in this excel file.`,
`Opening balance mentioned in the excel file can not be read.`; item variants
`Item Code/Item name is duplicated in this excel file.`, `GST number … cannot be read`,
`Sale price / Purchase price / Minimum stock quantity / Contact no. / Email-ID / Address /
Location mentioned in the excel file can not be read.`

### 4.1 Output folders (client)

```
/storage/emulated/0/Vyapar/
/Vyapar/Reports/        /Vyapar/Reports/Excel/     /Vyapar/Reports/JSON/
/Vyapar/Backup/         /Vyapar/Auto_Backup/       /Vyapar/Close_Books/
/Vyapar/Transactions/
```
Reports are saved as both **Excel** and **JSON** (the JSON folder implies a data-level export).
Share targets additionally land under `Documents/<subdir>` through MediaStore (`dta.S`).

### 4.2 Report export dialog (client)

`PdfExcelDialogUiModel(exportFileName=…)` with operations, verbatim from resource strings:
`export_operation_open_pdf | save_pdf | print_pdf | share_pdf | export_excel | open_excel |
share_excel | schedule_report | see_existing_schedule`. Per-report extra fields:
`report_export_include_aging_graph`, `report_export_include_invoice_details`,
`mfg_report_excel_duration`. Report-specific export option models:
`ItemSummaryExportSettings(salePrice=…)`, `DayBookExportSettings(showItemDetails=…)`,
`StockDetailExportSettings(showDateTime=…)`, `TdsReportExportSettings(showDateTime=…)`,
`AdditionalFieldsInExport` (`reports/reportsUtil/model/`).
Log tag `Print_pdf_report` / `Print_pdf_report`.

### 4.3 Scheduled reports — the one KMP-owned export (server + client)

`reportName=` `export_operation_schedule_report` and `ReportScheduleActivity`,
`ReportScheduleModel`, `ReportScheduleFrequency`, `ReportScheduleCreateApiRequestModel(companyId=…)`,
`ReportScheduleDeleteDto(companyId=…)`. Strings `vyapar.shared.domain.models.report.ReportScheduleModel`,
`vyapar.shared.data.remote.dto.reports.ReportSchedule{Get,Post}ApiResponse`,
`vyapar.shared.legacy.reports.scheduleReports.*` prove the KMP module owns this. Runner `o7n`
logs `SyncReportSchedulesUseCase: {companyId is blank, email must not be null for …, API failed
with, unexpected statusCode=, DB write failed:, failed to replace schedules in DB,
failed to read local schedule types}`. Endpoint `api/ns/scheduled-reports`.
⇒ **Delivery channel is server email** (client only stores/edits the schedule and mirrors it into
local SQLite); the actual periodic PDF/Excel generation for scheduled reports is server-side.
`getAllScheduleReportType` in `newreports/ReportUtil` enumerates eligible reports.

---

## 5. Sharing (client intents; links minted server-side)

`ShareUtilsActivity` is the single funnel. Extras it consumes: `INTENT_EXTRA_TITLE/SUBJECT/BODY/
MIMETYPE/URI`, `reminder_type` (0..9 dispatcher in `D0()`), `share_txn_list`, `party_id`, `item_id`,
`_from_date`, `_to_date`, `theme_id`, `single_color`, `double_color`, `mark_copy_option`,
`show_as_delivery_chalan`, `share_txn_mesg`, `additional_phone_number`, `Event Map`.
`g78.c()/g()` build the intent; `g78` also opens a `c4c` "Share" bottom sheet for the
single-invoice ≤5-item image case.

**MIME enumeration** (`qd8.D(path, kind, subject, body, email)`), effectively the output
artifact type system:

| kind | mime |
|---|---|
| 1 | `application/pdf` |
| 2 | `application/vnd.ms-excel` |
| 3 | `image/png` |
| other | `text/plain` |

Attachment URI via `FileProvider.getUriForFile(ctx, packageName, file)` (`qd8.E`),
`GRANT_URI_READ`, `Intent.createChooser(..., "Send Email")`. The Flutter side has its own
provider (`dev.fluttercommunity.plus.share.ShareFileProvider`, authority
`in.android.vyapar.flutter.share_provider`, plus `SharePlusPendingIntent` receiver).

**WhatsApp (client, with a server notify):** `https://api.whatsapp.com/send?phone=` +
`setPackage("com.whatsapp")`; direct-launch probe with fallback
(`"WhatsApp launched"`, `"WhatsApp did NOT launch, opening system share tray"`,
`"neither WhatsApp variant present"`); experiment flags `isWhatsappTxnMsgExperimentEnabled`,
`is_direct_whatsapp_sharing_working`, `whatsapp_silent_launch_check_delay_millis`,
log prefixes `[WAShare] {Invoice,Item,Statement,Business card,Payment reminder,Service reminder}
WhatsApp share status/launched successfully`, `[WAInvoiceShare] Channel selected: …`,
`[WAShareFallback] Silent fail: getFileExtension == -1 for …`.
Consent/permission is a first-class flow: `activities.WhatsappPermissionActivity`,
`GsonModels/WhatsAppCommPermissionModel/Response`, `IS_WHATSAPP_TEMPLATE_SHOWN_ONCE`,
`WhatsappPermission Not Accepted`, and POST `api/ns/whatsapp/transaction/notify` (**server**).
Message body is produced client-side by `r7g.y(...)` / `r7g.z(txnType)` — an HTML/plain-text bill
summary (date, invoice no, per-item `name  qty: rate`, discount, tax, total, balance, days-due)
ending in the CTA `https://billing.vyaparapp.in/txn`. Payload DTO:
`GsonModels.SaleTxnWhatsAppMsg{date, invoiceNo, itemList, totalAmount, balance, totalBalance,
daysDue, saleType, paymentMode, companyName, partyName, partyMobileNum, countryCode,
billDetails:f9d, isLicencedUser}`. `TxnSMSPOJO` + `api/ns/sms/vyapar/party/transaction/v2` +
`api/ns/sms/vyapar/party/reminder` are the SMS twins. Editable before send via
`in.android.vyapar.MessageToSendToParty` (`activity_message_to_send_to_party`).

**Share-as-image (client):** `SETTING_INVOICE_SHARE_AS_IMAGE` / `VYAPAR.INVOICESHAREASIMAGE`
(`useTemplateForImage`), `createBitmapFromView(WebView,String,Bitmap)` (+ `"createBitmapFromView
returned null"`), `ReminderImage.png` written to a cache dir, `_png` pseudo-mime used throughout
`g78`/`veo`. Gallery save uses the Flutter plugin `com.mhz.savegallery.saver_gallery`
(`SaverGalleryPlugin`, `MediaStoreUtils.createImageUri/scanUri`) and Java
`MediaScannerConnection`.

**Share links (server):** `GsonModels/GenerateShareLinkRequest{catalogueId, List<Integer> itemIds}`
→ `api/catalogue/generateItemSharingUid`; `PartyTxnStatementShareLinkRequest{currentCompanyId,
initialCompanyId, partyId, cleverTapId, deviceId, firmId, firebaseToken, platformId}` →
`api/ns/party-ledger/link` (`generatePartyTxnStatementShareLink`, cached per party in
`newDesign/partyDetails/datastore/VyaparPartyLedgerSharedPreferences.fetchAllPartyTxnStatementLinkIdsMap`);
`AskPartyDetailsShareLinkRequest{uuid, partyId, partyName, cleverTapId, companyDBName, phoneNumber,
emailId, companyName, companyAddress, companyLogoHex, shippingAddress, billingAddress, gstin,
gstinType}` + `AskPartyDetailsShareLinkResponse` — "ask the party for their details" over the
network rather than re-typing. `AttachmentUploadURL(url=…)` / `TxnUploadURL(url=…)` /
`AttachmentDetails(downloadUrl=…)` confirm **URLs come back from the server**; binaries go to S3
(`api/ns/cloud/file/signed-urls`, `api/ns/cloud/file/multiple-signed-urls`, `/api/sync/file`,
logs `fetchS3LinkForAttachment`, `Error while uploading file to S3:`, `Unknown HTTP response
during fetchS3LinkForVyaparNetwork`). Local attachment tables: `attachments`,
`transaction_attachments`, `item_adj_attachment_mappings`, `task_attachments`
(`task_attachments.remote_path as url`). Graceful degradation string:
`invoice attachment lost, fell back to text-only`.

**Vyapar Network (B2B invoice push, server):** `vyaparNetwork/util/ShareInvoiceOnVyaparNetworkCoroutineWorker`,
`api/ns/vyapar-network/send-invoice`, `api/ns/vyapar-network/fetch-s3-urls`,
`VyaparNetworkInvoicePreviewActivity` reads pref key `s3HtmlUrl` — i.e. the **server stores the
rendered invoice HTML in S3** and the receiver's app re-views it; `VyaparNetworkS3LinksResponse(url=…)`,
`VyaparNetworkInvoiceImageClickedResponse(transactionId=…)` (read-receipt-style analytics),
`AUTOSHAREINVOICESONVYAPARNETWORK`, `VyaparNetworkWebAppInterface` (WebView bridge),
`VyaparNetworkInvoicePreviewActivity`.

**Catalogue / Online Store (server storefront, client admin):** `catalogue.CatalogueActivity` is a
fragment host (not a PDF generator) with `ViewStoreFragment`, `EditStoreDetailsFragment`,
`ItemStockMoreOptionBottomSheet`, `StoreReportActivity`; `OrderListActivity` hosts a WebView whose
JS bridges are `catalogue/orderList/OnlineOrderWebAppInterface` and `StorePreviewWebAppInterFace`
(methods `onWhatsAppIconClick`, `onPhoneIconClick` → `api.whatsapp.com/send?phone=`,
`setPackage("com.whatsapp")`; JSON parsed with Gson into `ndi`). Preview entry point uses
`https://billing.vyaparapp.in/wastore`. Payload `GsonModels/CatalogueRequest{catalogueId,
CompanyDetails{…deliveryCharge, customCharge, enableOnlineOrdering, currencySymbol, decimalPrecision,
countryCode, cleverTapId, fcmToken…}, itemImages:[CatalogueRequestImageModel], itemsList:
[catalogue.models.CatalogueItemModel], deletedItemIds, deleteItemImageIds, deviceId,
initial_company_id}`. Endpoints `api/catalogue/create/v2`, `api/catalogue/update/v2`,
`api/catalogue/host-name`, `api/catalogue/catalog-lead`, `api/catalogue/getCountOfOrdersAndViews`,
`api/ns/catalogue-metrics/v2/{catalogue_id}`, `api/ns/vendor-order-dashboard/order-status/{catalogue_id}`.
Worker `catalogue/sync/CatalogueSyncWorker`, `orderList/UpdateOnlineOrderStatusWorker`.
DB side: `create table kb_catalogue_items(catalogue_item_id integer primary key autoincrement,
catalogue_item_code varchar(32) unique, catalogue_item_name, catalogue_item_description,
catalogue_item_sale_unit_price double, catalogue_item_category_name default 'General', …)` plus
`items.item_catalogue_status / item_catalogue_sale_unit_price / item_catalogue_description /
item_catalogue_stock_status` and `item_images.item_image_catalogue_sync_status`.
Store-front identity keys `VYAPAR.CATALOGUEID/CATALOGUEUID/CATALOGUEALIAS/CUSTOMDOMAIN`,
`getCustomDomainUrl`, `CustomDomainLeadRequestModel(catalogueAlias=…)` (lead = marketing funnel).
Client settings: `CATALOGUEMINIMUMORDERAMOUNT(ENABLED)`, `CATALOGUEDELIVERYCHARGE*`,
`CATALOGUECUSTOMCHARGE*`, `CATALOGUETAXESENABLED`, `CATALOGUEONLINEITEMDISCOUNTENABLED`,
`CATALOGUEONLINEORDERENABLED`, `CATALUELINKSTOCKENABLED`, `CATALOGUEV2IMAGESDELETIONPENDING`.
Other share-y bits: `greetings/uilayer/views/WhatsappCardsActivity` +
`greeting_round_whatsapp_template`, `greeting_custom_first_share_template`,
`new_greeting_card_*` templates; `referral` (`general_referral_link:
https://mobile.vyaparapp.in/billing-software`); `nps/ShareNPSFeedbackCoroutineWorker`;
`sendDesktopDownloadLinkToWhatsapp`; `share_with_accountant_whatsapp_number_input` +
`accountant_whatsapp_number` + `share_report_to_ca_whatsapp_design` (CA = chartered accountant).

---

## 6. "Bank statement" — assumption correction

`in.android.vyapar.BankStatement` is **not** an importer. It extends
`AutoSyncBaseReportActivity`, inflates `activity_bank_statement*`, and renders an HTML report out of
the *local ledger* (`M1()` builds
`<h2 align="center"><u>Bank Statement</u></h2><h3>Bank name: …</h3>` +
`<th>Date / Description / Withdrawal Amount / Deposit Amount / Balance Amount` and iterates
`yo1` transaction rows, mapping transaction-type ids to descriptions). Screen strings:
`bank_statement_filter_bank_label`, `bank_statement_filter_status_label`,
`bank_statement_balance_summary_label`, `bank_statement_deposit_label`,
`bank_statement_withdrawal_label`, `bank_statement_no_of_transactions`,
`bank_statement_no_data_desc`, `bank_statement_no_permission_message`,
`bank_statement_upgrade_required_message` ⇒ premium-gated **report**, event tag
`BANK_STATEMENT_REPORT`. Related: `loanaccounts` / `Loan Statement Summary`,
`select_party_for_party_statement`, `party_statement_report_view_filter_list`,
`share_party_statement`.

I found **no** bank-statement *file* parsing: no `MT940`, `camt`/`pacs.008`, `CSVParser`,
opencsv or `.csv` reader strings; no `importBank`/`uploadStatement` classes. The only file
imports in the app are items/parties `.xls`/`.xlsx` (§4) and payment-gateway KYC documents
(`api/ns/payments/accounts/{account_id}/documents`, `uploadPaymentDocument`). Note also POI's
`emulateCsv`/`OldExcelExtractor` are internal. GST return-adjacent upload exists as
`api/ns/gstr/ca-details/form`.

---

## 7. e-invoice / e-way bill artefacts (server-rendered)

Schema (client, in `transactions`): `txn_irn_number varchar(256)`, `txn_einvoice_qr varchar`,
`einvoice_id varchar(256)`, `einvoice_status integer not null default 0`, `einvoice_metadata text`,
`cancelled_einvoice_date datetime`, `txn_eway_bill_number varchar(50)`,
`txn_eway_bill_api_generated integer`, `txn_eway_bill_generated_date datetime`.
Endpoints (all server): `api/ns/gsp/einvoice/login|generate|print`, `api/ns/gsp/einvoice`,
`api/ns/gsp/eway/login|generate|verify|print`, `api/ns/ewaybill/valid/{gstin}`,
and for UAE: `api/ns/ib-einvoice/AE/signin`, `api/ns/ib-einvoice/AE/invoices/{txnType}/{uuid}/download`
(`einvoicePdf=` field, `UaeEInvoiceDownloadError(message=…)`). Client-side POJOs
`GSPRegisterViaEWayResponse(data=…)`, `EWayLineItemRequest(productName=…)`,
`EInvoiceCancelSuccess(cancelDate=…)`, `getGspLoginData()Ljava/util/Map;`,
`havent_registered_for_gsp`, `gsp_login_otp_screen_text`, `Error while generating e-Invoice`,
`Remove e-invoice txn from report`. ⇒ The **signed** IRN PDF is obtained from the GSP through
Vyapar's backend; the app only prints/renders it. QR on the invoice itself (`txn_einvoice_qr`) is
drawn client-side; `SETTING_PRINT_GULF_COUNTRY_QR_CODE` switches the Gulf variant, and the
`*_gulf.html` asset variants show the bank/signature block differs per country.

---

## 8. What a clean-room clone needs (opinionated summary)

1. **Two renderers, one HTML source of truth.** Emit invoice HTML from data + per-theme CSS, then
   (a) `WebView.createPrintDocumentAdapter` → `PrintManager` for real printers and
   → `onLayout/onWrite` into a MediaStore file for PDF; (b) same HTML reflowed at N chars/line for
   thermal. Vyapar literally does this and nothing else — no PDF library.
2. **Theme registry is 16 regular + 5 thermal + mobile-friendly + 2 landscape + Tally**, with a
   numeric theme id stored in prefs (`VYAPAR.TXNPDFTHEME`, `TXNPDFTHEMECOLOR`,
   `TXNPDFDOUBLETHEMECOLOR`, `TXNTHERMALTHEME`), and paid/free gating on theme *and* branding footer.
3. **Item table = list of {key, header label override, enabled, width ratio, order}** persisted as
   `VYAPAR.PRINTITEMHEADERSORDER` + per-column `PRINTITEM*ENABLED`/`*COLUMNRATIOVALUE` keys. The
   resize screen is a separate pass over the same list.
4. **Thermal transports must be three** (BT SPP / TCP 9100-ish / USB) with a per-device
   `{id,name,ip,port}` table, a default address pref, 1 s connect timeout + 3 retries, cached
   connection, and an ESC/POS command layer covering `ESC !` styles, char-count layout/padding,
   `GS ( k` QR, line feed, auto-cut, cash-drawer kick, ISO-8859-1 (plus a "native language"
   bypass that switches to HTML-image mode).
5. **Excel = HSSF `.xls` only for writing**; accept `.xls|xlsx` for reading with a generated sample
   file whose columns mirror the current feature flags; write into `Documents/Vyapar/Reports/Excel/`.
   Skip DOCX entirely — the original's XWPF code is unreachable.
6. **Share = one activity + one intent builder + 4 MIME kinds**, with a direct-WhatsApp fast path
   (explicit package, silent-launch verification, fallback to chooser) and analytics around it.
   The per-party statement/item links are **server-minted** and cached; binaries ride on
   S3 signed URLs. A clone either stands up that backend or mints links locally.
7. **Scheduled/automated report delivery is server-side** (KMP only manages the schedule rows), and
   so are IRN/e-way PDFs — plan these as a backend contract, not app code.
8. Do **not** implement bank-statement import from this target: it does not exist here.

---

## 9. Evidence index (commands actually run)

```
ls work/jadx/sources/in/android/vyapar/                     # 236 java files, partial
grep -E '^Lin/android/vyapar/' work/dex/classes.txt | wc -l # 598
grep -iE 'print|thermal|escpos|printer' work/dex/classes.txt
grep -oE 'VYAPAR\.[A-Z_0-9]+'  <strings -n 6 -t x classes*.dex> | sort -u
grep -oE 'SETTING[A-Z_0-9]*'   <same>                        | sort -u
grep -oE '"Print_[A-Za-z0-9_]+' <same>                       | sort -u   # 90+ labels
grep -iE 'GetFileForTransaction|createPdf|ThermalFont' <same>
unzip -l work/apks/base.apk | grep -iE 'theme.*\.html|fonts/'  # 18 template assets
jq -r '.url' work/dart_out/scripts.jsonl | sort -u            # 1111 paths, all adkrity_app
work/venv/bin/droidasc findrefs work/apks/base.apk string SETTING_PRINT_PAGE_SIZE
work/venv/bin/droidasc findrefs work/apks/base.apk string "GetFileForTransactionUsecase"
work/venv/bin/droidasc findrefs work/apks/base.apk type EscPosConnectionException
work/venv/bin/droidasc findrefs work/apks/base.apk type HSSFSheet        # pl1, sl1 (+POI)
work/venv/bin/droidasc findrefs work/apks/base.apk type XWPFDocument     # 4 hits, all POI
work/venv/bin/droidasc findrefs work/apks/base.apk type XSSFSheet        # app: none
work/venv/bin/droidasc findrefs work/apks/base.apk extends android.print.PrintDocumentAdapter
work/venv/bin/droidasc findrefs work/apks/base.apk string "https://billing.vyaparapp.in/txn"
work/venv/bin/droidasc findrefs work/apks/base.apk string "vcshare"      # BusinessProfileActivity
work/venv/bin/droidasc findrefs work/apks/base.apk string "wastore"      # OnlineOrderWebAppInterface
work/venv/bin/droidasc findrefs work/apks/base.apk string "SyncReportSchedulesUseCase"  # o7n
work/venv/bin/droidasc getclass  ... TxnPdfActivity|ExportItemsActivity|BankStatement|\
  ThermalPrinterActivity|AddWifiThermalPrinterActivity|zxn|p4c|ms9|js9|dta|uy0|ovo|k82|phn|alp|xa6|gr5|zfh|oyn|g78|qd8|hm0|eij|izn|mvo|veo
```
