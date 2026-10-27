# Vyapar RE — Run Record

Target: /home/ubuntu/mkqt/vyp/vyapar.apks (APKCube repack; sha of original recorded at work/apks/)
Package: in.android.vyapar v31.0.1 (vc 728), minSdk 29, targetSdk 36, compileSdk 37.

## Gates
- G1 deliverable: clean-room feature map + reimplementable spec (analysis-report form); later a clone app. No patched APK shipping.
- G2 env: java, jadx, apktool, aapt2 (arm64 host — aapt binary in build-tools is wrong-arch/unusable, use aapt2 or droidasc getmanifest), python3.12, droidasc (work/venv), aotopsy v1.6.0 (verified sha256, work/tools/aotopsy). No device/adb/frida → static only.
- G3 code location (observed): hybrid, three layers.
  1. Java/dex (7 dex, 43,632 classes; 598 own in/android/vyapar/*): business logic, DB ownership (224 CREATE TABLE strings incl. journal_entry, chart_of_accounts_mapping, audit_trails, currencies), Retrofit API, reports/settings activities. Names NOT obfuscated.
  2. Flutter (libapp.so Dart ~3.5.4 banner; aotopsy dart_meta structural label 3.5.0): UI layer, classes/functions obfuscated (--obfuscate) BUT scripts.jsonl keeps package: source paths and string literals survive in string_refs.jsonl. Method-channel bridge strings e.g. AndroidTransactionHelper.transact_26b9a8.
  3. Kotlin Multiplatform/Compose: assets/composeResources/vyapar.shared.generated.resources/drawable/*.xml, org/koin/compose in dex — some features (likely payment-gateway KYC, m2d) are Compose.
- G4 baseline: original files kept untouched at work/apks/; extracted artifacts are copies.

## Datasets
- work/apks/base.apk, split_config.arm64_v8a.apk (libapp.so, libflutter.so extracted to work/native/)
- work/dex/classes.txt (slash descriptors), work/dex/manifest.txt, work/dex/raw/classes*.dex (for `strings`)
- work/dart_out/ (aotopsy: classes.jsonl 3903, functions.jsonl 23112, string_refs.jsonl 19646, call_edges.jsonl 123905, scripts.jsonl)
- work/jadx/sources/ (background jadx, may be partial; droidasc getclass is authoritative)
- tools: work/venv/bin/droidasc {getclass,findrefs,listclass,getmanifest}; work/tools/aotopsy

## Monetization/SDK surface (observed from class list)
Razorpay checkout, Firebase (crashlytics/messaging), Adjust, CleverTap, Mixpanel, Microsoft Clarity, Truecaller SDK, Facebook share/login, sqflite (tekartik), Apache POI (xlsx/docx export), barhopper (QR), WorkManager, ktor-client, Koin DI.

## Clone toolchain (observed)
- Flutter 3.47.8 / Dart 3.13.7 WORKS on this arm64 host via git bootstrap (/home/ubuntu/flutter; the x64 release tarballs are irrelevant). ANDROID_HOME=/home/ubuntu/.android-sdk.
- Android SDK: platform-36 + build-tools 36 + licenses were installing at time of writing (task bd6qczxgq). No Chrome, no desktop toolchain (not needed). No emulator possible (arm64 VM, no KVM) → verification = compile + flutter test/Robolectric-class checks only; label artifacts accordingly.
- Clean-room directive from user: take inspiration from architecture/features; NEVER copy exact code, strings, or assets.

## Decisions
- RE handoff completed (2026-10-27): read the stopped Qoder workflow cache and independently completed the monetization audit plus the sync/backup, Dart/Flutter boundary, and masters/settings dimensions. Every claim in those four reports has a dated verification verdict. `work/FEATURE_MAP.md` now synthesizes all nine dimension reports into the clean-room product boundary. The previously mentioned `work/findings/gapfill.md` is absent and is not used as evidence. M1 may now start from the local-first scope and client/server boundary in the feature map.
- Continuation audit (2026-10-27): `work/FEATURE_MAP.md` and the prior `glassbill/` scaffold were absent when the native build phase was resumed. The available dimension reports establish the M1 boundary: local, two-tier storage (master registry plus one company database file), company/firm, settings, party, unit, tax-rate, and item masters. The new implementation will use independently named Room entities and domain models; the observed `vypmaster` / per-company `.vyp` storage topology is retained because it is an explicit M1 requirement. Server-backed features remain excluded.
- Glass theme (user reference repo qcom-band-menu, cloned to work/reference/, studied 2026-10-27): the exact look = **io.github.kyant0:backdrop** library (drawBackdrop + effects: blur(8dp), vibrancy(), lens(24dp,24dp), Highlight, Shadow, InnerShadow, LayerBackdrop) + **Miuix** (top.yukonga.miuix.kmp, Compose-Multiplatform UI kit) theming + custom DampedDragAnimation (spring-damped indicator drag with velocity squash/stretch + whole-bar panel offset spring-back + press scale 78/56 + tab scale→1.2 + haptics on tab tap). Repo's own code is GPL-3.0 → DO NOT copy their .kt files; write our own components with equivalent parameters/behavior; dependencies are Apache-licensed (verify at integration). Their two quality switches (isBackdropBlurEnabled/isLiquidGlassEnabled) map 1:1 to our full/blur-only/flat theme setting. minSdk 29 matches ours.
- Build policy (user decision 2026-10-27): NO APK/AAB compilation on this machine — GitHub Actions produces artifacts (.github/workflows/build.yml: assembleDebug + tests, artifact upload). Local verification gate = ./gradlew testDebugUnitTest only (JVM tests; no packaging). Label all local-only checks as prototype-grade until a CI-built APK is exercised.
- Stack (user decision SUPERSEDED 2026-10-27, final): NATIVE ANDROID
 — Kotlin + Jetpack Compose, Android-only for now. Flutter skeleton discarded (flutter build stopped, glassbill scaffold being replaced). iOS dropped. Web later, if ever, via Compose Multiplatform — therefore structure the app from day one with a pure-Kotlin core (domain/data logic, no android.* imports) so a future KMP split is a refactor, not a rewrite.
- Liquid glass now native: Haze (backdrop blur, Apache-2.0) + AGSL RuntimeShader refraction + Material3 spring motion; quality switch (full/blur-only/flat).
- Clean-room stands: features from FEATURE_MAP.md only; RE findings may inform architecture but no code/asset/string copying.
- UI skin: user wants "liquid glass" — implemented as toggleable theme layer: BackdropFilter glass chrome + spring motion + RuntimeShader refraction on Android; refraction-on-web needs verification (Flutter web shader support gap) — blur portion works there regardless.
- Flutter 3.47.8 installed at /home/ubuntu/flutter (arm64 via git bootstrap), Android SDK 36 + licenses accepted; verification = compile + flutter test only (no device/emulator).
- Clean-room only for clone: reimplement features; no copied code, assets, strings, or Vyapar branding/trademark.
- Server-authoritative features (sync backend, e-invoice GSP, payment gateway KYC, license, OTP) → own backend or stub in clone; documented in scope matrix.
