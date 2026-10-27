# Monetization — Vyapar (in.android.vyapar v31.0.1 vc728)

Scope: plan tiers, trial, feature gates, purchase surface, referral rewards, and the separation between client UI and server authority. Evidence is static only: DEX strings, manifest/class inventory, `droidasc`, and the verified server-api report. This is a clean-room feature record; it does not authorize reuse of names, copy, assets, endpoints, or implementation.

## Findings

- **Observed — Plan vocabulary and tiered upgrade surface.** The packaged resources include Free-trial and premium states, plus Gold, Silver, and Platinum plan markers. Dedicated activities exist for pricing, licence upgrade, referral rewards, and scratch cards. This establishes a tiered plan UX, not the entitlement rules for any individual feature.
  - Evidence: `rg -n -i 'gold|silver|platinum|free.?trial|premium' /tmp/dexstrings.txt`; `rg -n -i 'planandpricing|refer|scratch' work/dex/classes.txt`.

- **Observed — Trial policy is remotely configurable.** A bundled configuration literal enables a seven-day Gold trial that downgrades to Free, excluding IN, PK, NP, and BD. It is a shipped default/configuration candidate, not proof that every user receives the trial at runtime.
  - Evidence: `sed -n '109880,109920p' /tmp/dexstrings.txt`.

- **Observed — Free usage is limited at least by sale-invoice count.** The DEX contains free-sale invoice count, weekly-limit, current-count, and limit-reached symbols alongside trial-expiry presentation states.
  - Evidence: `rg -n -i 'freeSaleInvoiceCount|free_sale_invoices_per_week_count|getWeeklyFreeSaleTxnLimitReached|FreeInvoiceLimitReached|free_trial_expir' /tmp/dexstrings.txt`.

- **Observed — Licensing/purchase is website-first rather than an embedded Google Play Billing integration.** `PaymentWebsiteActivity` hosts a JavaScript-enabled WebView, has `BUY`, `ATTACH_LICENSE`, `RENEW`, and `UPGRADE` modes, and explicitly passes an `AndroidJSInterface` into the page. No `com.android.billingclient` implementation class occurs in the full class inventory. This does not rule out all Play Store purchase attribution code: Adjust's analytics library carries purchase/subscription types.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/PaymentWebsiteActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk 'Lin/android/vyapar/PaymentWebsiteActivity$a'`; `rg -c '^Lcom/android/billingclient/' work/dex/classes.txt`.

- **Observed — Entitlement validation is server-authoritative.** The verified server map records plan retrieval, licence attachment, licence redemption, pricing, and referral-reward routes. A packaged `plans.json` fallback exists, but it is not an authority that a clone should trust for entitlements.
  - Evidence: `rg -n 'tp/plans|get-s3-path|license|pricing|referral-rewards' work/findings/server-api.md`; `unzip -l work/apks/base.apk 'assets/web/dist/assets/config/plans.json'`.

- **Observed — The client has both premium and role-based gates.** DEX markers include premium-sheet/report/theme locks, while `UserPermissionModel` is a small parcelable model and the app has a No Permission bottom sheet plus user-role management. These are distinct concepts: paid entitlement and staff authorization must be represented separately in a clone.
  - Evidence: `rg -n -i 'premium.*(report|theme)|ShowPremiumBottomSheet|NoPermissionBottomSheet|UserManagementActivity' /tmp/dexstrings.txt work/dex/classes.txt`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/models/UserPermissionModel`.

- **Observed — Referral is part of the commercial surface.** There are dedicated referral/refer-and-earn/scratch-card activities and verified server routes for reading cards, updating a scratch-card state, and redeeming a licence.
  - Evidence: `rg -n -i 'ReferAndEarnActivity|ReferralRewardsActivity|ReferralScratchCardsActivity' work/dex/classes.txt`; `rg -n 'referral-rewards' work/findings/server-api.md`.

- **Observed — Razorpay is bundled, but the available evidence places it in customer payment-gateway/KYC flows, not plan subscription.** The app packages 311 Razorpay classes and has an online-payment/KYC feature family. `PaymentWebsiteActivity` itself is the plan-purchase host observed above.
  - Evidence: `rg -c '^Lcom/razorpay/' work/dex/classes.txt`; `rg -n 'paymentgateway|PaymentWebsiteActivity' work/dex/classes.txt`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/PaymentWebsiteActivity`.

- **Observed — There is no display-ad SDK evidence.** The only `com.google.android.gms.ads` entries are Advertising ID helpers, and the Flutter source paths describe an ad-creative product rather than an in-app display-ad SDK. Attribution/analytics SDKs are not evidence of ads being shown in the billing app.
  - Evidence: `rg -n '^Lcom/google/android/gms/ads/' work/dex/classes.txt`; `jq -r '.url' work/dart_out/scripts.jsonl | rg '^package:adkrity_app'`.

## Clone boundary

M1–M5 stay local-first and ungated. A future subscription implementation needs a `PlanEntitlement` interface whose trusted implementation is a GlassBill-owned backend; local UI must treat it only as a presentation state. Referral, remote plan retrieval, payment-gateway KYC, server licence attachment, and commercial analytics are out of the initial clone scope.

## Verification pass — 2026-10-27

Re-ran the commands recorded above after the Qoder workflow was killed. The tier vocabulary, seven-day payload, WebView modes, absent Play Billing classes, entitlement endpoints, referral activities, Razorpay class inventory, and Advertising ID-only Google Ads entries were **CONFIRMED**. The exact remote rollout/eligibility of the trial, plan prices, and per-feature entitlement matrix remain **UNVERIFIED** because no live server calls or authenticated runtime session were used.
