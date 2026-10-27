# GlassBill

GlassBill is a clean-room, offline-first Android bookkeeping application. Its M1 scope is defined in the repository-level [feature map](../work/FEATURE_MAP.md).

## Structure

- `domain`: pure Kotlin business models and calculations.
- `data`: pure Kotlin repository ports and transfer models.
- `app`: Android/Compose UI and Room persistence edge.

## Local verification

Do not build APKs or AABs locally. The only permitted local gate is:

```bash
./gradlew testDebugUnitTest --max-workers=2
```

GitHub Actions is responsible for Android artifacts.

## SDK note

The app remains minSdk 29 and compile/target SDK 36. The current Apache-licensed Backdrop and Miuix artifacts are compiled with SDK 37 metadata, so `android.experimental.disableCompileSdkChecks=true` is deliberately scoped in `gradle.properties`. The code retains a Flat visual fallback; CI/runtime validation is required before relying on shader paths beyond API 36.

AGP 9.1's built-in Kotlin is temporarily disabled because the M1 Room schema still uses KAPT. Migrate Room to a built-in-Kotlin-compatible processor path before removing this compatibility setting.

## Local arm64 hosts

AGP's bundled AAPT2 is x86-64. On an arm64 host, add the following line to a local (gitignored) `gradle.properties` override before running the gate:

```
android.aapt2FromMavenOverride=/path/to/arm64/build-tools/35.0.2/build-tools/aapt2
```

GitHub Actions runners are x86-64 and do not need this override.
