# Sync and Backup — Vyapar (in.android.vyapar v31.0.1 vc728)

Scope: local backup/restore, Google Drive backup, multi-device sync, and sharing/users. Static evidence only. “Server-authoritative” is a scope conclusion, not a request to contact or replicate the original service.

## Findings

- **Observed — Storage has a per-company backup-ready topology.** The master company registry stores auto-backup status/duration, last-backup time, sync enablement, and a sync-company identifier; the database report independently establishes the master `vypmaster` plus per-company `.vyp` files.
  - Evidence: `rg -n -i 'create table kb_companies|comp_auto_backup|sync_company_global_id' work/findings/db-schema.md`; `rg -n -i '\\.vyp|vypmaster' work/findings/db-schema.md`.

- **Observed — Local backup/restore is a file workflow.** `OpenBackupActivity` accepts ACTION_VIEW/ACTION_EDIT URIs, handles the `file` scheme, and requests legacy external-storage permission. Its DEX strings include an invalid-backup state, a configurable filename, a backup-file path, and a `/Vyapar/Auto_Backup/` location.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/activities/OpenBackupActivity`; `rg -n -i '/Vyapar/Auto_Backup|vyp_backup_file_path|backup_filename|data_backup_file|InvalidBackupFile|Backup file saved' /tmp/dexstrings.txt`.

- **Observed — Automatic Drive backup is a first-class client feature.** The setting activity reads/writes enablement, duration, and reminder keys; the service starts a single coroutine job, observes network changes, and logs completed backups. The archive requests the `drive.file` OAuth scope.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/DriveAutoBackupSettingActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/Services/GoogleDriveAutoBackupService`; `rg -n 'googleapis.com/auth/drive.file' /tmp/dexstrings.txt`.

- **Observed — Remote full-database upload is separate from local/Drive backup.** `DumpUploadWorker` creates/gets a dump ZIP and uploads a `vyapar_db_dump` file through the Retrofit API, with a Remote Config-controlled cadence. This is a server feature, so it must not be reproduced against the original endpoint.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/workmanager/DumpUploadWorker`; `rg -n '/api/dbBackupV3' /tmp/dexstrings.txt`.

- **Observed — Multi-device sync replays ordered server changelogs into the local database.** The DEX contains `/api/sync/changelog`, company/download and user-role routes; its logs say it sends changelog queries, executes server changelogs when local state is behind, and persists a new local changelog version. The checkpoint key is `VYAPAR.CHANGELOGNUMBER`.
  - Evidence: `rg -n -i 'Sending changelog queries|executing changelogs received|updated last changelog|api/sync/(changelog|v2/company|company/download)|VYAPAR.CHANGELOGNUMBER' /tmp/dexstrings.txt`.

- **Observed — Sync-and-share includes an invited-user/role-management surface.** The sync screen exposes a role-sensitive view, while the profile form routes role-id and existing permission JSON into the editor. The role editor receives a role id and serialized existing permission state.
  - Evidence: `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/syncAndShare/activities/SyncAndShareActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/syncAndShare/activities/UserProfileFormActivity`; `work/venv/bin/droidasc getclass work/apks/base.apk Lin/android/vyapar/syncAndShare/activities/RolePermissionEditorActivity`.

- **Observed — P2P transfer is an accounting feature, not peer-to-peer database sync.** The app contains `P2pTransferActivity` plus paid/received transaction markers. The separate changelog endpoints and sync-company identifiers are the evidence for synchronization.
  - Evidence: `rg -n -i 'P2P_PAID|P2P_RECEIVED|P2pTransferActivity|api/sync/changelog' /tmp/dexstrings.txt`.

## Clone boundary

M1 retains local company files and implements local export/import only after the core database exists. Define backup and sync ports in pure Kotlin, but provide no original-service client. Later local backup should write a validated, versioned GlassBill archive; a future GlassBill cloud backend may implement ordered sync independently. Multi-user sharing, remote dump upload, and Drive authorization are deferred/stubbed.

## Verification pass — 2026-10-27

All claims above were re-run from the stated commands and **CONFIRMED**. The binary reveals no complete on-device format specification for a local backup archive or a server changelog payload; those formats are **UNVERIFIED** and must be designed independently for GlassBill.
