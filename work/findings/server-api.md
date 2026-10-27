# Vyapar (in.android.vyapar v31.0.1 / vc728) — server-api findings

Dimension key: `server-api`
Scope: complete map of the network surface the client depends on, so that a clean-room
clone can draw its own client/server boundary deliberately instead of by accident.
Method: static only (`strings` over 7 dex, `droidasc getclass/findrefs`, jadx output where
it had landed, `aotopsy` Dart dumps, `resources.arsc` string scan, decoded manifest).
No network calls were made; nothing below is verified against a live server.
Secret values are truncated to prefix/suffix throughout.

---

## 1. Hosts and environments

Every Vyapar-owned host found in the binary (deduped from `strings -n 8` over
`work/dex/raw/classes*.dex`):

| Host | Role | Stack that uses it |
|---|---|---|
| `https://api1.vyaparapp.in` | primary REST base | Java/Retrofit (`Lpl0;` hardcodes `https://api1.vyaparapp.in/` in all three builders) |
| `https://api2.vyaparapp.in` | secondary/failover REST base | KMP `vyapar.shared` (`Ltmm;->b` == api2 in prod); also `Ltmm;->a()` picks api1 vs api2 from a feature flag |
| `https://socket1.vyaparapp.in` / `https://socket2.vyaparapp.in` | realtime WebSocket (sync push) | `Lc8n;->f` (classes6) |
| `https://staging.vyaparapp.in` | staging build flavour | `Ltmm;->a()` / `Lc55;->b` (uses `public.stagingapp.pem` instead of `public.prodapp.key`) |
| `https://vpg.vyaparapp.in/index.html#` | licence / pricing web portal | KMP, hidden behind the byte-shift obfuscation (`Luyi;->e`) |
| `https://mobile.vyaparapp.in/billing-software` | marketing/referral landing link | share templates |
| `https://billing.vyaparapp.in/{txn, vcshare., wastore, phone-billing, invoice-billing-pdf}` | branded short-link prefixes embedded in SMS/WhatsApp/invoice-footer templates | `classes3.dex` HTML/SMS templates |
| `https://vyaparapp.in/{permission, terms, terms#permissions, web/FAQ, refer-and-earn, distributor/create?platform=android, api/upi, api/upi?pn=}` | legal/help pages + UPI deep-link URL | help/permission screens, UPI intent |
| `https://vyaparwebfiles.vypcdn.in/tds/tds-rates.html` | CDN-hosted TDS rate table page | `Lp5;->invoke` |
| `https://vyapar.go.link/deeplinks` | install-referrer App Link (the only `autoVerify` http host in the manifest) | deep links |
| `https://vyaparapp.brands.live/?bs=` | BrandsLive (B2B marketplace) hand-off | `Lv6a;->a` |
| `https://app.adkrity.com/api/v1/`, `https://adm.adkrity.com/api/v1/`, `https://adkrity.app.link/ad/` | AdKrity advertising back end | Dart module (`package:adkrity_app/...`) |
| `https://outline.truecaller.com/v1/`, `https://sdk-otp-verification-noneu.truecaller.com/v{1,3}/otp/…` | Truecaller SDK servers | `Lm4q;`, `Lnvi;` |
| `https://api.razorpay.com/v1/checkout/…`, `https://ifsc.razorpay.com/{ifscCode}`, `https://checkout.razorpay.com/…`, `https://cdn.razorpay.com/static/otpelf/` | Razorpay checkout / IFSC / OTP autofill | Razorpay SDK + **Vyapar's own** `getIfscCodeDetails` |
| `https://api{,-eu,-in}.mixpanel.com/{track,engage,groups}` | Mixpanel ingest | mixpanel session-replay/analytics lib |
| `https://www.clarity.ms` | Microsoft Clarity ingest | `Lwz;` (KMP) initialises `ClarityConfig` |
| `https://firebaseremoteconfig.googleapis.com/v1/projects/…`, `firebaseinstallations`, `firebaseappcheck`, `vyapar-af84.firebaseio.com` | Firebase | Firebase Android components registered in manifest |

Allowed first-party hosts are whitelisted as a literal set in `Ltmm;->Z`:
`{"vyaparapp.in", "staging.vyaparapp.in", "vyapar.com"}` — used to validate/clean incoming URLs.

**Environment switch.** `Ltmm;->a()` is the single base-URL oracle: a compiled enum
(`smm.a[sq0.c.ordinal()]`) selects prod vs staging, and inside prod a *remote* boolean
(`ioi.k3[37]`, fetched through a Koin-scoped settings object) decides api1 vs api2.
Derived web bases are string-concatenations of that oracle: `{base}/web/vyapar-network`,
`{base}/web/razorpay-client-dashboard/login?mid=`, `{base}/gst-accounting-pc`, `{base}/home/?client_type=1&workflow=4`.
So "which server" is itself server-controllable — the clone should keep a single
configurable base and not replicate the remote host flip.

---

## 2. Three independent HTTP stacks (they do not share a client)

| # | Stack | Base | Where | Notes |
|---|---|---|---|---|
| S1 | Java + Retrofit/Gson, `in.android.vyapar.Retrofit.ApiInterface` | `api1.vyaparapp.in` | `Lpl0;` (classes3) — 3 lazily-cached Retrofit instances (a/b/c), OkHttpClient 60 s connect/read/write, each with an interceptor chain (`new nl0(<interceptor>,0)` + `new nl0(zvq.S(),2)`) | 55 declared methods, 47 distinct annotated paths; response wrapper `Lmy2;` (Call) |
| S2 | Kotlin Multiplatform `vyapar.shared.*` (Ktor client present: `Lio/ktor/client/…` 46 classes; Retrofit annotations also relocated into `defpackage`) | `api2.vyaparapp.in` (`Ltmm;->b`) | classes6, `*m0` service classes (`hm0, lm0, sm0, wm0, zm0, an0`) | 289 distinct `vyapar.shared.…` type names in the dex pool; DTO package `vyapar.shared.data.remote.dto.*` |
| S3 | Dart `http`/`dio` in the embedded Flutter module | `app.adkrity.com` / `adm.adkrity.com` | `work/dart_out/string_refs.jsonl`, `scripts.jsonl` (`package:adkrity_app/main-module/services/network/APIProvider.dart`) | The Dart layer is the AdKrity advertising side-car, **not** the billing backend. Its own `MixPanelManagerDummy`, `FirebaseAnalyticsManagerDummy`, `RelayAnalytics` classes are dummies |

Endpoint-path distribution confirms the split: 34 `/api/...` (leading-slash) literals live
in classes3 (S1); 44 `api/...` (relative) literals live in classes6 (S2).

**Consequence for the clone:** one client, one base URL, one API module. The triplication in
Vyapar is historical, not functional.

---

## 3. Endpoint table by feature domain

Verb legend is inferred from the R8-relocated Retrofit annotation shapes and then
cross-checked on `ApiInterface.java` (see §3.1): **G**=GET, **P**=POST, **U**=PUT, **D**=DELETE, **W**=server-rendered WebView page, **R**=path present but verb unresolved.
Stack legend: S1=Java/api1, S2=KMP/api2, S3=Dart.

### 3.0 How the verb legend was derived (annotation recovery)
R8 repackaged `retrofit2.annotation.*` into `defpackage` names. Shapes recovered with
`droidasc getclass` + a correlation count over the decompiled interface:

| obf | shape | = | evidence |
|---|---|---|---|
| `Li49;` | `String value()` | **GET** | 19 methods annotated; `grep -A1 '@i49' \| grep -c '@s82'` = **0** bodies |
| `Lshh;` | `String value()` | **POST** | 31 methods; 29 carry `@s82`/`@xmh` |
| `Lwhh;` | `String value()` | **PUT** | 4 methods; 4/4 carry `@s82`, all 4 also carry `@n1i` path var |
| `Ldia;` | `hasBody/method/path` | **@HTTP** | jadx emits `method="DELETE"` verbatim |
| `Le6g;` | marker | @Multipart | paired with `Lxmh;`(value+encoding = @Part) |
| `Lzt8;` | marker | @FormUrlEncoded | paired with `Ly18;`(value+encoded = @Field) |
| `Ldjp;` | marker | @Url | only use is `getIfscCodeDetails(@djp String)` with **no** path annotation |
| `Lvka;` `Lxka;` `Ljla;` | | @Header / @HeaderMap / @Headers | |
| `Lm5j;` `Ln1i;` `Ls82;` | | @Query / @Path / @Body | |

Feature→endpoint mapping (this is the load-bearing table):

| Feature | Endpoint | V | Stack | Client method / caller (verified) |
|---|---|---|---|---|
| **OTP signup (own, not Firebase)** | `/api/ftu/v3/send/otp?country_code=&mobile=` | G | S1 | `ApiInterface.getOtp` |
| | `/api/ftu/v3/verify` (body `v4q`) | P | S1 | `ApiInterface.verifyOtp` |
| **Sync account** | `/api/ns/auth/login` (body `l5n`) | P | S1 | `syncLogin` |
| | `/api/ns/auth/check-user` | P | S1 | `syncVerifyUser` |
| | `/api/ns/auth/reset-password/send-otp` , `/api/ns/auth/reset-password` | P | S1 | `syncOTPRequestToResetPwd`, `syncNewPwd` |
| | `/api/ns/auth/user-details` | G | S1 | `getFullAuthToken` → `FullAuthTokenResponse` |
| | `/api/passcode/reset/mobile?passcode=&email=&phone=` | G | S1 | `requestPasscodeReset` (app-lock passcode) |
| **DB dump / premium backup** | `/api/dbBackupV3` multipart; 13 text parts + file part `vyapar_db_dump` | P | S1 | `dumpDbToServer`, only caller `in.android.vyapar.workmanager.DumpUploadWorker->f`; cadence from keys `db_dump_config/db_dump_interval/take_latest_db_dump_after` |
| | `/api/sync/off`, `/api/sync/upgrade`, `/api/sync/company-display-name` | R | S2 | `hm0`/siblings |
| **Changelog sync** | `/api/sync/changelog`, `api/sync/v2/company`, `api/sync/v2/companies`, `api/sync/file`, `/api/sync/company/download/`, `api/sync/v2/company/users/`, `…/users/role`, `…/users/delete` | R | S2 | `Lhm0;-><clinit>` constants |
| | `api/sync/v2/company/users/{company_global_id}`, `…/users/{id}` | G | S1 | `getUserProfileList`, `fetchUserProfile` |
| **Realtime push** | `wss://socket{1,2}.vyaparapp.in?platform=1&clevertapId=&companyId=&deviceId=` | WS | S2 | `Lc8n;->f` (OkHttp WebSocket, `yyl(…, ["websocket"])`); subscribe frame = `token`, `company`, `prev_socket_id`; changelog cursor from `setting_key='VYAPAR.CHANGELOGNUMBER'` |
| **Licensing** | `/api/ns/referral-rewards/redeem-license` (body `anj(String,Integer,String)`) | P | S1 | `redeemLicense`, caller `Linq;->g` |
| | `/api/ns/referral-rewards/cards?locale=`, `…/scratch-card` | G / U | S1 | `getScratchCards`, `updateScratchCardsState` |
| | `/api/ns/tp/plans`, `/api/ns/tp/plans/get-s3-path`, `/api/ns/license/auto-attach`(→`/api/ns/tp/license/auto-attach`), `/api/ns/license/leads`, `/api/ns/pricing` | R | S2 | decoded from shifted arrays in `Lryi;` (see §5) |
| | `api/ns/pricing/get-sale-info` | R | S2 | `hm0` neighbour classes |
| | `https://vpg.vyaparapp.in/index.html#`, `/pricing`, `/my-account`, `/my-account/attach-license`, `/multiple-license` | W | S2 | `Luyi;->e/k/r`, `Lryi;->b/c/d` (decoded) |
| **Coupons / sale info** | `/api/assign/coupon/splten` (form fields `mobile,email,country_code`) | P(form) | S1 | `getCouponDiscount` |
| **GSTIN verification** | `/api/ns/ewaybill/valid/{gstin}?platform={int}&addr={bool}` | G | S1 | **two** methods on the same path: `getGstinDetails`→`paymentgateway.model.GstinModel` (full record) and `isGstinValid`→`f9d` (boolean). `platform` = GST-system code (1/2/3… for regular/SEZ/composition), `addr` asks for the address block |
| | `api/ns/ewaybill/valid`, `api/ns/gst`, `api/gst/hsn` | G/R | S2 | `hm0.C` |
| | `/api/gst/hsn?client_type=1&search={q}` | W | S1 | `Liai;->…` builds `g49.m(tmm.a().concat("/api/gst/hsn"), "?client_type=1&", "search="+q)` then `WebView.loadUrl` — HSN/SAC pickers are a *server-rendered page*, not JSON |
| **E-Way Bill / E-Invoice (GSP proxy)** | `api/ns/gsp/signup`, `…/gsp/einvoice/login`, `…/gsp/eway/login`, `…/gsp/otp/send`, `…/gsp/otp/verify`, `…/gsp/eway/verify`, `…/gsp/einvoice`, `…/gsp/eway`, `…/gsp/einvoice/generate`, `…/gsp/eway/generate`, `/api/ns/gsp/einvoice/print`, `/api/ns/gsp/eway/print` | R | S2 | `hm0.D…I`; DTOs `vyapar.shared.data.remote.dto.einvoice.GSPLoginSendOTPResponse / GSPRegisterViaEInvoiceRequest / GSPRegisterViaEWayVerifyOTPRequest` |
| **UAE e-invoice** | `/api/ns/ib-einvoice/AE/signin`, `/api/ns/ib-einvoice/AE/invoices/{txnType}/{uuid}/download` | R | S2 | `Lan0;`/`Lzm0;`, DTO `UaeEInvoiceSignInResponse` |
| **GSTR** | `api/ns/gstr/ca-details/form`, `api/ns/gstr/send/wa-email` | R | S2 | `Lhm0;->v/w`; own class `AutoSyncBaseReportActivity` |
| **IFSC / bank** | **`https://ifsc.razorpay.com/{ifscCode}`** | G | S1 | `getIfscCodeDetails(@djp String url)` — `@Url` with **no path**, so the caller supplies the absolute URL; the only such literal in classes3 is `https://ifsc.razorpay.com/$ifscCode`. Vyapar calls Razorpay's public IFSC API directly, not its own host |
| | `/api/ns/payments/accounts/bank-details?bank_account_no=&bank_ifsc_code=` | G | S1 | `getBankAccountInfo` |
| | `{base}/view/ifsc/find-ifsc?ifscCode=` (with `Authorization: Bearer` header on `loadUrl`) | W | S1 | `in.android.vyapar.paymentgateway.kyc.activity.IFSCWebViewActivity` |
| **Payment gateway (mPoS / current account) KYC** | `/api/ns/payments/accounts` (POST/GET), `/api/ns/payments/accounts/{account_id}` (GET/PUT), `/api/ns/payments/accounts/documents` (GET), `/api/ns/payments/accounts/{account_id}/documents` (POST multipart, part `type`) | P/G/U | S1 | `createPaymentAccount`, `getPaymentAccount`, `updatePaymentAccount`, `downloadDocuments`, `uploadPaymentDocument` |
| | `api/ns/payments/v2/accounts/`, `api/ns/payments/v2/payment-links`, `api/ns/payments/v2/token?accountId=` | R | S2 | `Lqm0;->invokeSuspend` etc.; v2 token feeds the webview `login_url` intent extra |
| | `{base}/web/razorpay-client-dashboard/login?mid=`, `{base}/view/payment-gateway/charges`, `{base}/view/check-online-payments/`, `{base}/view/partner-store` | W | S1/S2 | `Ltmm;->Y` + `Liai;`/webview activities (`LoginWebviewActivity`, `ChargesWebviewActivity`, `KycVerificationActivity`, `OnlinePaymentWebviewActivity`, `TermsAndConditionWebviewActivity`) |
| | `upi://pay`, `https://vyaparapp.in/api/upi?pn=` | intent | S1 | `Lr7g;->y`, ` Leip;` — collect-payment hand-off to a UPI app |
| | Razorpay SDK: `/v1/checkout/public?platform=android&version=1.7.20&library=checkoutjs&build=`, `/v1/magic/order?`, `/v1/payments/`, `/v2/settings/sdk`, `/v2/preferences?key_id=` | R | SDK | checkout library, not Vyapar's contract |
| **Catalogue / online store** | `/api/catalogue/create/v2`, `api/catalogue/update/v2`, `/api/catalogue/generateItemSharingUid`, `/api/catalogue/getCountOfOrdersAndViews?catalogueId=`, `/api/catalogue/host-name?alias=`, `/api/catalogue/catalog-lead` | P/G | S1 | `createCatalogue`, `generateItemsShareLink`, `fetchCatalogueStats`, `getCustomDomainUrl`, `sendCustomDomainLeadData` |
| | `/api/ns/catalogue-metrics/v2/{catalogue_id}` | R | S1 | literal present in classes3, holder class unresolved (second, non-`ApiInterface` Retrofit surface) |
| | `/api/ns/vendor-order-dashboard/order-status/{catalogue_id}` (U), `/view/vendor-order-dashboard/orders/` (W) | U/W | S1 | `updateOnlineOrder`; `Lexg;->invoke` |
| | custom domain = `{alias}.vyaparapp.in` | | S1 | `.vyaparapp.in` suffix literal + `custom_domain_go_daddy_india_url` / `custom_domain_go_daddy_general_url` / `custom_domain_reseller_account_type` keys; `https://vyaparapp.in/distributor/create?platform=android` |
| **Party invite / share links** | `/api/ns/party/invite` (POST, `AskPartyDetailsShareLinkRequest`), `/api/ns/party/invite/company-url`, `/api/ns/party/invite/update-url/{url_id}` (PUT), `/api/ns/party-ledger/link` | P/U | S1 | `generateAskPartyDetailsShareLink`, `generatePartyInviteLink`, `updateInvitePartyLink`, `generatePartyTxnStatementShareLink` — the **server mints the share URL**; the client only renders it. `{base}/web/party-ledger` renders the ledger |
| **SMS (server-sent)** | `/api/ns/sms/vyapar/party/reminder`, `/api/ns/sms/vyapar/party/transaction/v2` (both accept single + array body) | P | S1 | `sendPaymentReminder`, `sendBulkPaymentReminder`, `sendTxnSms`, `sendBulkTxnSms` (body `TxnSMSPOJO.TxnSMSRequest[]`) |
| **WhatsApp** | `/api/ns/whatsapp/transaction/notify`, `/api/mobile/wa/opt-in` | P | S1 | `sendWhatsAppMsgTo`, `sendWhatsAppCommPermission`; caller `in.android.vyapar.activities.WhatsappPermissionActivity->B0`; `/api/ns/gstr/send/wa-email`, `/api/ns/billbook` (MBB request) |
| **Party suggestion (V2V)** | `/api/ns/v2v/lok`, `/api/ns/v2v/bmnd/{req_type}`, `/api/ns/v2v/mnd/{job_id}` | P/U/G | S1 | `sendPartySuggestion`, `startProcessForSuggestedParties`, `getSuggestedParties`; callers `newDesign.partyListing.suggested.network.SuggestedPartyWorker->d/f`, `V2VNotificationWorker->d` |
| **Inbound-call → party** | `api/ns/check-inbound-call` | R | S2 | `hm0.g`; Truecaller payload builder `getTrueCallerPayLoad()/getTrueCallerSignature()` |
| **Scheduled e-mail reports** | `/api/ns/scheduled-reports` (GET list, POST create, PUT, `@HTTP(hasBody=true, method="DELETE")`) | G/P/U/D | S1 | `getReportSchedulesTypes?companyGlobalId=`, `createReportSchedule`, `deleteReportSchedules`; DTOs `vyapar.shared.data.remote.dto.reports.*` |
| **Settings search index** | `/api/ns/docdb/settingsearch` | P | S1 | `dumpSettingsSearchData`, caller `workmanager.SettingsSearchDumpWorker->d` |
| **Analytics / telemetry** | `/api/ns/analytics/dump/v2` (body `vph`), `api/ns/device/usage`, `api/ns/user-tracker/save-location`, `api/ns/user-experiments`, `api/ns/referral-customer/experiment/assign` | P/G | S1/S2 | `getVyaparUser`, `sendUsageData`; `Lwm0;`, `Lhm0;->f/e` |
| **Feedback / leads / demo** | `/api/app-feedback` (body `RateUsModel`), `/api/ns/app-feedback`, `api/ns/leads`, `api/ns/demoreq/v2`, `/api/referral?license_code=&need_auth=`, `/api/referral/first/sale-save` | P/G | S1 | `appFeedback`, `sendMbbRequestDetails`, `callReferralApi`, `callFirstSaleSaveApi`; `NpsFeedbackPayload`, `DemoRequestDto` |
| **Report/file share via S3** | `/api/ns/cloud/file/signed-urls`, `api/ns/cloud/file/multiple-signed-urls` | P | S2 | `hm0.q/y`; DTOs `AttachmentUploadURLDTO`, `S3FileData`, `ShareReportUploadFormRequest/Response`, `VyaparNetworkS3LinksUpload` → **server issues S3 presigned PUT URLs; client uploads direct to S3** |
| **Vyapar Network (CA/accountant)** | `api/ns/vyapar-network/fetch-s3-urls`, `…/send-invoice`, `{base}/web/vyapar-network` | R | S2 | `hm0.p/r`, `Ltmm;->d`; DTOs `VyaparNetworkFetchS3LinksPayload(partyPhone=…)`, `AstroVyaparCompanyDetailsPayload(authToken=…)` |
| **Multi-currency** | `api/ns/multi-currency` (DTOs `ExchangeRateRequest/Response`) | R | S2 | `Llm0;->invokeSuspend` — exchange rates are **server-authoritative** |
| **Voice billing (Vaani / Talk2Bill)** | `api/ps/talk2bill/session`, `api/ps/talk2bill/jobs`, `api/ps/parse-address` | R | S2 | `hm0.x/z/A/B`; DTOs `vyapar.shared.domain.vaani.{SignedUrlRequestItem, SignedUrlResponse, VaaniPollResponse, VaaniServerResponse, VaaniSessionStatus}` → record → server transcribes → poll → parse address → prefill txn |
| **Hardware store** | `/api/ns/vyapar-hardware/order-delivery/status` | R | S2 | `Lsm0;->invokeSuspend`; DTO `ThermalPrinterOrderDeliveryStatusResponse` |
| **AdKrity token** | `api/ns/adkrity/token`, `/api/ns/brands-live?query=` | R | S2 | `Lhm0;->u`, `Lv6a;->a`; DTOs `AdKrityTokenReq/Res` → Vyapar's server issues the token that the Dart module then uses against `app.adkrity.com` |
| **Referral share text** | JSON blob with `general_referral_link`, `msg_type_no_action`, `msg_type_youtube`, `msg_type_web_url`, `msg_type_in_app_action` (WhatsApp prefills) | P | S1 | classes3 literal; short links `https://billing.vyaparapp.in/phone-billing`, `https://mobile.vyaparapp.in/billing-software` |

### 3.1 Header/credential contract (S1)
* `Authorization: Bearer <token>` — the constant is literally `Lnvi;->KEY_REQUEST_HEADER = "Authorization"`, `ACCESS_TOKEN_PREFIX = "Bearer %s"`. 44 of the 55 `ApiInterface` methods take an `@vka("Authorization") String` parameter, i.e. the caller supplies the header per call.
* Token resolution: `Lymd;->b(String scope)` → `in.android.vyapar.util.c.m().t(scope + "_auth_token")`, falling back to the global `m().f()`; returns `"Bearer ".concat(tok)`. **The auth token is stored per-company in SharedPreferences** — there is no OS keystore/EncryptedSharedPreferences on this path.
* `@Headers({"Accept: application/json"})` on ~20 methods, `Content-Type: application/json` on POST bodies, `@FormUrlEncoded` on the coupon endpoint only.
* Push registration is part of the auth surface: `firebase_token` is a named part of `dumpDbToServer`, and `sp_fcm_token` is a persisted pref.

---

## 4. Third-party server/SDK surface (existence only)

| SDK | Present | Identifier location | Masked value |
|---|---|---|---|
| Adjust | yes, initialised in app code | `in.android.vyapar.application.VyaparApp->onCreate`: `new com.adjust.sdk.AdjustConfig(zyg.s(), "10qnu5…auo", "production")` then `Adjust.initSdk(...)`, `Adjust.getAdid(...)` | app token `10qnu5…` (12 ch), env **production** (hardcoded — no sandbox) |
| CleverTap | yes (push, inbox, AMP, in-app) | manifest meta-data | `CLEVERTAP_ACCOUNT_ID = 886-…-9W5Z`, `CLEVERTAP_TOKEN = 61b-…` (7 ch), `CLEVERTAP_USE_GOOGLE_AD_ID=1`, `CLEVERTAP_INAPP_EXCLUDE` = 14 named activities |
| Firebase | yes, project `vyapar-af84` | `resources.arsc` | `project_id`/`google_app_id` = `vyapar-af84`, RTDB `https://vyapar-af84.firebaseio.com`, bucket `vyapar-af84.appspot.com`, two `google_api_key` values `AIzaSyAR_4…5wcc` and `AIzaSyBuHAM…DgtQ`; registrars compiled in: Analytics, App Check (debug + **Play Integrity**), Auth, **Realtime Database**, **Firestore**, Messaging, Crashlytics, Performance, Remote Config, ML Kit |
| Microsoft Clarity | yes, KMP-side init (`Lwz;` builds `ClarityConfig`) | manifest has only kill-switches | `DisableTouchEvents=true`, `DisableLayouts=true`, `DisableSessionReplay=true`; project id **not** in manifest (supplied at runtime) |
| Mixpanel | **partial only** — `com.mixpanel.android.sessionreplay.*` (387 strings) and `jsonlogic`, but `mpmetrics.MixpanelAPI` is absent from `classes.txt` and has zero refs | hosts `api/api-eu/api-in.mixpanel.com` | no token found in the Java/KMP layer; used via `MPSessionReplayInstance` in `Lpnd;`(classes3) and `Lsbl;`(classes6) |
| Truecaller | yes — full SDK (`com.truecaller.android.sdk.*`) | manifest `com.truecaller.android.sdk.ClientId = @7F122117`, resource name `trueCallerClientID` | value not extracted (see §6) |
| Razorpay | yes (checkout + `RazorpayInitializer`, `CoreInitializer` via androidx.startup) | deep link `razorpay://in.android.vyapar` | key ids arrive per-session from `/api/ns/payments/v2/token` |
| Facebook | yes (login + share) | manifest `com.facebook.sdk.ApplicationId/ClientToken` → `@7F120F95/@7F120F96`; resource `facebook_app_id`, `facebook_client_id`, `fb_login_protocol_scheme`, value `fb487152…` | app id is the public FB id |
| Google Drive | yes — auto backup | `Services.GoogleDriveAutoBackupService`, `DriveAutoBackupSettingActivity`, scope `https://www.googleapis.com/auth/drive.file`, backup folder pref `vyapar_backup` | |
| AdKrity | yes — separate Flutter module | Dart constants `app.adkrity.com/api/v1/`, `adm.adkrity.com/api/v1/` | |
| ML Kit / Voice | yes | `MLKitInitializer`, `api/ps/*` | |

**Truecaller, in detail (assignment item c).** Three distinct integrations, all real:
1. **Login/identity (OAuth + PKCE)** — `in.android.vyapar.newftu.SignUpActivity->onCreate` builds
   `new TcSdkOptions.Builder(ctx, callback).sdkOptions(32).buttonColor(crimson).buttonTextColor(white).loginTextPrefix(0).ctaText(1).buttonShapeOptions(128).footerType(4).build()`,
   gates on `TcSdk.getInstance().isOAuthFlowUsable()`, then `setOAuthState / setOAuthScopes / setCodeChallenge / getAuthorizationCode(this)`, and round-trips `onActivityResultObtained(...)`. Result is posted to `https://outline.truecaller.com/v1/` via Retrofit interface `Lnvi;` (`@POST/@GET` on `n4q.KEY_VERIFIED_PROFILE`, header `Authorization: Bearer %s`) → `TrueProfile`.
2. **OTP autofill** — `Lm4q;` creates two more Retrofit services against
   `https://sdk-otp-verification-noneu.truecaller.com/v1/otp/client/installation/` and (separately in the pool) `/v3/otp/installation/`, with `sdk_variant` / `sdk_variant_version` string resources as interceptors; Razorpay's `cdn.razorpay.com/static/otpelf/` is a second autofill route.
3. **Call screening / inbound-call attribution** — `wy2.getCallRejectorInstance(ctx)`, `gpb incomingCallListener`, permission probes for `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`/`CALL_PHONE`, and the server round-trip `api/ns/check-inbound-call` plus `getTrueCallerPayLoad()/getTrueCallerSignature()`.
   There is also a fallback constructor `createInstanceForFallback(...)` that calls `dxk.showDisclaimer(...)`.
The app also ships a Truecaller privacy-notice URL (`developer.truecaller.com/phone-number-verification/privacy-notice`) and the consent string "your phone number will be verified by Truecaller…". **Net:** phone-number verification is *not* Firebase Phone Auth — `com.google.firebase.auth.PhoneAuthProvider` appears only inside `com.google.android.gms.internal.firebase-auth-api` parcel glue, never in app code. OTP is Vyapar's own `/api/ftu/v3/*`, with Google `SmsRetriever` (`AUTO_READ_OTP_SMS_RETRIEVER_API_*` events) and the Truecaller OTP SDK as autofill aids.

---

## 5. Notable mechanism findings

1. **Deliberate build-time string obfuscation.** `Lzyg;->k(byte[])` subtracts 1 from every byte:
   `for (b : arr) list.add((byte)(arr[i] - 1)); return dom.m0(...)`.
   Five holder classes in classes6 encode licence/pricing/URL strings with it: `Lryi;`, `Ltyi;`, `Luyi;`, `Lvyi;`, `Lwyi;`
   (only 6 callers of `zyg.k` total, so the set is closed). Decoded examples: `/api/ns/tp/license/auto-attach`,
   `/api/ns/license/leads`, `/api/ns/tp/plans`, `/api/ns/tp/plans/get-s3-path`, `/my-account/attach-license`,
   `https://vpg.vyaparapp.in/index.html#`, `web/dist/assets/config/plans.json`, and pref keys
   `license_code`, `plan_name`, `current_license_expiry_date`, `LICENSE_UPGRADE_STATUS`.
   This means the endpoint set is *not* discoverable by naive `strings` and `droidasc findrefs` misses all of it
   (both `vpg.vyaparapp.in` and `get-s3-path` returned zero hits). Any clone-side API inventory must decode the shift.
   Also relevant: the obfuscated set contains a base64 blob `wyi.k = WNmIb+…` (AES key material — not reproduced).
2. **Request-body hybrid encryption on the KMP path.** `Lc55;->b` generates an 8-digit nonce twice,
   SHA-256s it into an AES key, `AES/CBC/PKCS5Padding`s the payload, then `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`s
   the key with a public key read from **assets `public.prodapp.key`** (or `public.stagingapp.pem` when the base
   is neither api1 nor api2). `Lc55;->a` is the decrypt side (hardcoded AES key + IV constants). Five call sites
   (`Ldy3; L_e34; L_jyh; L_kyh; L_q3g;` in classes6) — so a subset of KMP endpoints expects
   `encrypted payload + RSA-wrapped key`, not plain JSON. The clone must decide whether to keep this contract at all.
3. **Sync is server-issued SQL changelogs, not a delta merge.** Evidence: string set
   `GetChangelogResponse(authorized=`, `CreateChangelogDto(companyGlobalId=`, `BehindChangelogModel(authorized=`,
   `ChangelogEventModel(companyGlobalId=`, log text `Get in sync, executing changelog`, `No queries coming in this
   changelog`, ` is ahead of received changelog version`, `DbBehindServerChangelogsExecuted`, plus the DB columns
   `change_logs varchar not null`, `view_changelog integer not null` and the pref row
   `setting_key='VYAPAR.CHANGELOGNUMBER'`. The server sends ordered queries with a monotonic number and the client
   replays them; the WebSocket (`Lc8n;`) is the invalidation trigger. **This is the single most consequential
   authority decision in the whole product** and the clone must choose explicitly (server-issued SQL vs. entity CRDT vs. pull-only).
4. **Two feature-flag channels decide runtime topology.** Firebase Remote Config drives
   `db_dump_config` (`db_dump_interval`, `take_latest_db_dump_after`) and `FirebaseRemoteConfigServerException`
   handling (`Llc;`/`Lq4h;`); a Koin-registered settings object `ioi.k3[37]` flips api1↔api2. `SyncAndShare` also
   renders `featureAvailabilityResult` from the plans payload. So licence-gating is partly server-side.
5. **Server-minted URLs everywhere.** Share links, catalogue hosts, S3 upload URLs, GSP/e-invoice print artefacts,
   IFSC page, HSN search page, party-ledger page, online-payment status page and the whole payment-gateway KYC
   journey are **pages/URLs returned by the server and displayed in a WebView with a JS interface**
   (`catalogue.orderList.OnlineOrderWebAppInterface`, `StorePreviewWebAppInterFace`,
   `paymentgateway.kyc.activity.*$paymentLoginInterface`, `IFSCWebViewActivity$IfscWebAppInterface`,
   `PaymentWebsiteActivity$JavaScriptInterface`). The offline escape hatch is a local asset base
   `https://localfiles/index.html#` (intercepted by a WebView asset scheme handler) and
   `assets/web/dist/**` (which contains `config/plans.json` — the same document the server serves from S3,
   i.e. a shipped fallback for pricing).
6. **Push is a command channel, not just a notification channel.** `FirebaseReceiverChild->onMessageReceived`
   branches on data keys `silent_action`, `cnc`, `nm`, `nt`, `title`, `body`, `image_url`, `clickAction`,
   `wzrk_bp`, `wzrk_dl`, and forwards CleverTap-origin payloads to `CleverTapAPI.getNotificationInfo`.
   A `silent_action` message makes the app perform server-requested work with no UI.

---

## 6. Client/server authority map (the boundary the clone must re-draw)

| Concern | Vyapar's split | Evidence strength | Clone recommendation |
|---|---|---|---|
| Invoice/txn arithmetic, GST/tax rounding, HSN *values* | **client** (local SQLite, 224 CREATE TABLEs, `hsn_data.json` shipped in assets) | observed | keep client-side; this is the product's offline selling point |
| GSTIN validity + name/address | **server** (`/api/ns/ewaybill/valid/{gstin}`; the client never talks to GSTN) | observed | own proxy or a stub with an explicit "not verified" state |
| IFSC → bank name | **server, but actually Razorpay's public host** (`https://ifsc.razorpay.com/{code}`) | observed | swap for a maintained IFSC dataset/RBI dump |
| HSN/SAC *search UI* | **server-rendered WebView page** (`/api/gst/hsn?client_type=1&search=`) even though `assets/hsn_data.json` ships locally | observed | reimplement as a native picker over the bundled dataset; drop the webview |
| Phone OTP | **server** (`/api/ftu/v3/send/otp` + `/verify`) — no Firebase Phone Auth | observed (negative + positive) | own OTP service; SMS Retriever autofill is client-only and portable |
| Payment-gateway account/KYC | **server + third-party web**: accounts CRUD on Vyapar's API, but the KYC/charges/dashboard journey is Razorpay pages in a WebView, and the session token comes from `/api/ns/payments/v2/token?accountId=` | observed | keep an adapter seam; the clone can stub or drop this whole vertical |
| E-invoice / e-way bill (GSP) | **fully server** — the client only orchestrates login/OTP/generate/print steps; nothing is signed locally | observed | server or stub; a clone cannot ship real e-invoicing without a GSP |
| Multi-device sync | **server-authoritative ordered SQL changelogs** + WebSocket invalidation | observed | pick a model explicitly; server-issued SQL is the fastest path to parity but the worst for security |
| Backup / restore | split: full-DB ZIP upload to `/api/dbBackupV3` (premium, WorkManager-driven, server-cadenced) **and** local file backup **and** Google Drive auto-backup | observed | keep local+cloud file backup; `/api/dbBackupV3` is a premium-server feature |
| Licensing / plan gating | split: licence string is *stored* client-side (prefs `license_code`, `current_license_*`, `LICENSE_UPGRADE_STATUS`) but *validated/attached* by server (`redeem-license`, `/api/ns/tp/license/auto-attach`, `get-sale-info`, `user-experiments`) and plans JSON is fetched from S3 with a bundled `plans.json` fallback | observed | the classic client-cache + server-authority split; replicate the shape, not the endpoints |
| Share links / public catalogue pages | **server mints every URL**; client only formats + hands to `api.whatsapp.com/send?phone=` / `mailto` / custom `{alias}.vyaparapp.in` domains | observed | clone needs its own public web host or the whole share feature is inert |
| SMS & WhatsApp transaction reminders | **server sends them** (`/api/ns/sms/…`, `/api/ns/whatsapp/transaction/notify`) — the client never uses the local SMS stack for reminders | observed | significant: a clone without an SMS/WhatsApp gateway silently loses a headline feature |
| Scheduled reports | **server e-mails them** (`/api/ns/scheduled-reports` CRUD) | observed | needs a scheduler on the clone's server |
| Analytics/telemetry | **server** (`/api/ns/analytics/dump/v2`, `device/usage`, `save-location`, `user-experiments`) + Adjust/CleverTap/Mixpanel-Replay/Clarity | observed | replace with own telemetry or omit |
| Voice billing (Talk2Bill/Vaani) | **server** (session, poll, transcription, address parse) | observed | drop or stub |
| Advertising (AdKrity) | **separate product**: own Dart UI + own back end; Vyapar only hands it a token (`api/ns/adkrity/token`) | observed | out of scope for the clone |
| Feature flags | **server** (Firebase Remote Config + `ioi.k3[37]` host flip) | observed | optional; keep a single static base URL |
| Deep links | `vyapar.go.link` (the only autoVerify host), custom schemes `vyaparapp://`, `upi://pay`, `razorpay://in.android.vyapar`, `fbconnect://cct.in.android.vyapar`, `genericidp|recaptcha://firebase.auth` | observed from manifest | clone defines its own App Link domain |

Unresolved / not claimed: the actual `trueCallerClientID`, `facebook_app_id` and `google_api_key`
*resource-value bindings* (the values are visible in the arsc pool but I did not complete the
ID→entry mapping), the holder of `/api/ns/catalogue-metrics/v2/{catalogue_id}`, the real HTTP verbs
for the S2 (KMP) endpoints — those are runtime-built, not annotation-built — and whether Firestore /
RTDB / App Check are exercised by first-party code or merely pulled in by the Firebase BoM and CleverTap
(registrars exist in the manifest; I found no first-party call sites, so this is treated as SDK-present).

---

## 7. Command log (reproducibility)

```
strings -n 8 -t x work/dex/raw/classes*.dex | grep -Eo 'https?://[…]' | sort -u        # host inventory
strings -n 3 work/dex/raw/classes*.dex | grep -aoE '(^|[^A-Za-z])/?api/(ns|ps|sync|…)' | sort -u    # 106 distinct api-path literals
droidasc getclass work/apks/base.apk Lin/android/vyapar/Retrofit/ApiInterface           # 55 methods
grep -A1 '@i49|@shh|@whh' …/ApiInterface.java                                            # verb/body correlation
droidasc getclass work/apks/base.apk "Lpl0;"                                             # base URL = api1 (x3)
droidasc getclass work/apks/base.apk "Ltmm;" / "Lc55;" / "Lhm0;" / "Lc8n;"               # env oracle, crypto, KMP endpoint table, socket
droidasc getclass work/apks/base.apk "Lzyg;" | grep -A12 'static String k'                # byte-1 obfuscation
python3 (shift-decode of ryi/tyi/uyi/vyi/wyi byte arrays)                                  # hidden licence URLs
droidasc findrefs work/apks/base.apk string "<url>" / method --class … / type …            # caller attribution
droidasc getclass work/apks/base.apk Lin/android/vyapar/newftu/SignUpActivity; …            # Truecaller OAuth path
grep -oE '<meta-data …/>' work/dex/manifest.txt                                            # SDK ids (masked)
unzip -p work/apks/base.apk resources.arsc > /tmp/vyapar_resources.arsc; strings -t d      # firebase project/keys
jq -r '.func' work/dart_out/string_refs.jsonl | grep -a -E 'https?://'                     # AdKrity Dart surface
```
