# Dart / Flutter UI Boundary — Vyapar (in.android.vyapar v31.0.1 vc728)

Scope: what the embedded Dart AOT payload owns, how it reaches Android, and what that means for a clean-room native clone. This report records architecture evidence only; no Dart source, strings, screens, or visual assets may be reused.

## Findings

- **Observed — The Dart payload belongs to an AdKrity sub-product rather than the billing core.** There are 160 first-party `package:adkrity_app/...` scripts, covering campaign setup, ad creation, design review, lead handling, integration, payments, and an AdKrity dashboard. The path inventory has no first-party invoice, voucher, stock, expense, or party feature module; the sole “Item” match is a generic web drawer item.
  - Evidence: `jq -r '.url' work/dart_out/scripts.jsonl | rg '^package:adkrity_app' | sort -u`; `jq -r '.url' work/dart_out/scripts.jsonl | rg '^package:adkrity_app' | rg -i '(txn|transaction|invoice|voucher|party|item|stock|expense)'`.

- **Observed — The Dart product has its own backend.** Dart string references include `app.adkrity.com` and `adm.adkrity.com` API roots and media URLs. That is distinct from the Java/Kotlin business API inventory in `server-api.md`.
  - Evidence: `jq -r '.value' work/dart_out/string_refs.jsonl | tr -d '\\000' | rg 'https://(app|adm)\\.adkrity\\.com' | sort -u`; `rg -n 'Dart layer is NOT the billing backend' work/findings/server-api.md`.

- **Observed — Flutter bridges through standard platform plugins.** The platform-channel record contains Pigeon channels for app/support/external/temp paths, SharedPreferences, URL launching, and a broad WebView bridge (file chooser, JavaScript channel, download listener, permission callback, navigation, and settings).
  - Evidence: `jq -c 'select(.channel_name | test("webview|url_launcher|shared_preferences|path_provider"))' work/dart_out/platform_channels.jsonl`.

- **Observed — Flutter embedding exists in the APK.** The DEX class inventory includes `FlutterActivity`, `FlutterFragment`, Flutter engine/plugin APIs, and `MethodChannel`/`BasicMessageChannel` APIs. This confirms an embedded Flutter runtime but does not make Flutter the owner of all app screens.
  - Evidence: `rg -n '^Lio/flutter/(embedding|plugin)/' work/dex/classes.txt | sed -n '1,80p'`.

- **Observed — The native/KMP side remains responsible for the billing model.** The DEX has Compose and Koin Compose classes and a small visible `vyapar.shared` class set, including transaction additional details, payment-term settings, a scan overlay, and mobile-POS view model. The broader reports tie voucher, stock, tax, reports, and settings to DEX/KMP data rather than Dart paths.
  - Evidence: `rg -c '^Landroidx/compose/' work/dex/classes.txt`; `rg -c '^Lorg/koin/compose/' work/dex/classes.txt`; `rg -n '^Lvyapar/shared/' work/dex/classes.txt`; `rg -n 'Flutter/Dart layer adds no business tables' work/findings/db-schema.md`.

- **Observed — Compose resources cover billing feature areas, but are not clone assets.** The APK contains generated Compose resource packages with assets for barcode scanning, reports, invoice output, sync, payment, loyalty, and settings; their names corroborate KMP feature coverage but cannot be copied.
  - Evidence: `unzip -l work/apks/base.apk 'assets/composeResources/vyapar.shared.generated.resources/*' | awk 'NR>3 {print $4}' | rg -v '/drawable/flag_'`.

## Clone boundary

GlassBill is intentionally native Kotlin/Compose. Do not port the embedded Dart payload or reproduce the AdKrity creative marketplace. Keep the pure-Kotlin domain/data boundary independent of Android UI so a future Compose Multiplatform client is feasible, but treat it as a new product architecture rather than a Flutter migration.

## Verification pass — 2026-10-27

All path, host, channel, embedding, and KMP/Compose inventory claims above were re-run and **CONFIRMED**. Precise Dart-to-native method-call semantics and runtime navigation are **UNVERIFIED** because this static pass did not execute the APK.
