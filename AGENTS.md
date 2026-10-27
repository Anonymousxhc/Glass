# AGENTS.md — GlassBill project (handoff contract)

Read this fully before acting. This project was started in a Qoder session; you are continuing it.

## What this is
1. **RE phase (mostly complete):** reverse-engineering `vyapar.apks` (in.android.vyapar v31.0.1 vc728, GST billing/inventory app) into a verified feature map.
2. **Build phase (starts now):** a CLEAN-ROOM clone in **native Android — Kotlin + Jetpack Compose**, app id `com.glassbill`, project dir `glassbill/`. Android-only. minSdk 29, compile/target 36.

## Where things live
- `work/RECORD.md` — the running decision log + gates. Keep updating it.
- `work/findings/*.md` — 9 RE dimension reports (db-schema, txn-model, reports-tax, print-export, server-api, monetization, sync-backup, dart-ui, masters-settings) + `gapfill.md`. Each claim labeled observed/inferred/unverified with exact evidence commands.
- `work/FEATURE_MAP.md` — the authoritative synthesis (written when RE completes; if missing, it is still in progress by the previous session — check `/workflows` run dir journal or wait for it).
- `glassbill/` — the Compose skeleton (being scaffolded; if absent/incomplete, finish it yourself per work/RECORD.md versions notes).
- `work/reference/qcom-band-menu/` — user's visual reference (GPL-3.0): the liquid-glass bottom bar. Its glass = Apache-licensed libs **io.github.kyant0:backdrop** (blur 8dp + vibrancy + lens(24,24), Highlight/Shadow/InnerShadow, LayerBackdrop) + **top.yukonga.miuix.kmp** theming + custom damped-drag indicator animation (press-scale 78/56, tab scale→1.2, bar panel-offset springs back with spring(1f,300f,0.5f), velocity squash/stretch).
- Datasets: `work/apks/base.apk`, `work/dex/{classes.txt,manifest.txt,raw/}`, `work/dart_out/*.jsonl`, tools at `work/venv/bin/droidasc` and `work/tools/aotopsy`.

## NON-NEGOTIABLE RULES
1. **Clean-room.** Never copy code, strings, drawables, or layouts from `base.apk`/dex/Dart datasets or from the GPL reference repo. Use the FEATURE MAP (facts, parameters, behavior) and open-source libraries (kyant0 backdrop, Miuix, AndroidX) instead. GPL repo code may be READ to understand behavior; reimplement from understanding with your own structure.
2. **NO APK/AAB builds on this machine.** User compiles via GitHub Actions. Local gate only: `./gradlew testDebugUnitTest` (+ lint if quick). Never run assembleDebug/bundle locally.
3. **Verify or label.** Feature claims used in code must trace to a findings report (prefer `observed`; flag `unverified` in code comments/PR notes). Keep the live record in work/RECORD.md updated (decisions, refuted claims, dead routes).
4. **Machine limits:** 2 CPU cores — gradle with --max-workers=2, -Xmx1g daemon, no filesystem-wide `find`s.
5. Server-authoritative features (sync backend, e-invoice GSP, payment gateway, licensing/OTP, share-link storefronts) are OUT of clone scope initially — build local-first + stub interfaces per the client/server boundary table in FEATURE_MAP.md.

## Glass theme requirement (user's explicit ask)
"Exactly the qcom-band-menu bottom-bar liquid glass — but as a THEME, not one widget." Implement via backdrop lib as reusable composables behind `GlassBillTheme`: glass floating nav bar (tabs: Dashboard/Items/Party/Bills/Reports… per feature map), glass sliders, glass sheets/dialogs/top-bars; quality setting full/blur-only/flat (maps to their isLiquidGlassEnabled/isBackdropBlurEnabled switches); pure-Kotlin-friendly separation so a future Compose-Multiplatform port stays cheap (domain/data modules android-import-free).

## Milestones (from FEATURE_MAP.md when it lands)
- M1: Room schema (per-company `.vyp` + `vypmaster` two-tier design, see db-schema.md) + company/party/item masters + glass shell → CI APK #1
- M2: sale/purchase/receipt/payment vouchers + GST calculation engine + invoice numbering
- M3: reports (GSTR-1/2/3B, stock, P&L, party statement) + PDF/print + thermal
- M4: reminders (payment/service/SMS/WhatsApp), loyalty, multi-currency, TDS/TCS
- M5: long tail: manufacturing, fixed assets, bank statement import, Vaani-voice equivalent (local), recycle bin, FY operations, backup/restore local files
Each milestone = compiling + unit tests green + pushed so user's GitHub Actions produces the APK.

## Pending from previous session (do NOT duplicate)
- A lean RE workflow is finishing the last 4 dimension reports, then a verification pass will re-audit them (user explicitly wants that restored — wait for it or perform it yourself: re-run each claim's evidence commands, mark confirmed/refuted in the findings file).
- A scaffold agent is creating `glassbill/` with resolved AGP/Kotlin/Gradle/Compose-BOM/backdrop/miuix versions; check its SETUP.md before regenerating anything.

## First actions for you (Codex)
1. Read work/FEATURE_MAP.md (or findings/*.md if map not yet written) + RECORD.md.
2. Audit `glassbill/` state; finish versions/bootstrap if the scaffold agent didn't land.
3. Commit an initial git history (local only; pushing/remote setup is user's call — ask before adding remotes).
4. Start M1 per milestones above. Update RECORD.md as you go.
