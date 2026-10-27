# Monetization — Vyapar (in.android.vyapar v31.0.1 vc728)

Scope: how the app makes money and gates features. Evidence from dex string pool
(/tmp/dexstrings.txt, single `strings -n 6 classes*.dex` dump), dex/raw, manifest.txt,
dart_out/*.jsonl, base.apk via droidasc.

## 1. Plan tiers

- Three paid tiers named **Gold / Silver / Platinum** (plus **Free**):
  - `ic_gold_premium_21dp`, `ic_gold_premium_small`, `ic_silver_premium_21dp`,
    `ic_silver_premium_small`, `ic_vyapar_platinum_21dp`, `ic_vyapar_platinum_small`
  - `buy_gold`, `buy_silver`, `SILVER`, `Silver`, `call_customer_support_vyapar_platinum`
  - `buddha_gold` (id-looking constant, likely a licence/plan code)
- Server-driven trial config (verbatim block in dex strings):
  ```
  {
      "is_feature_enabled": true,
      "trialAttributes": {
          "exclusionList": [ "IN", "PK" , "NP" , "BD" ],
          "trialDays": 7,
          "trialExpiredPlanType": "Free",
          "trialPlanType": "Gold"
  ```
  => 7-day Gold trial that downgrades to plan "Free"; trialled only for countries
  OUTSIDE India/Pakistan/Nepal/Bangladesh (exclusion list).

## 2. Purchase flow / payment rails

(sections appended below as evidence lands)
