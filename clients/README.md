# RomSafe clients

Both apps share one Kotlin Multiplatform **engine**, so the planning, hashing, conflict and retry
logic is written once.

```
clients/
  shared/        Kotlin Multiplatform: API client (Ktor), sync engine, ROM downloads, library model
  android/       Jetpack Compose app: SAF folder pickers, WorkManager background sync
  desktop/       Compose Desktop app for Windows: tray icon, folder pickers, file watching
```

## Shared engine

- A `FileTree` interface with two implementations: Android SAF tree URIs (`DocumentsContract`)
  and `java.nio` on desktop. It covers listing, reading, atomic writes and modified times.
- `SyncSession` / `SyncEngine`: for each mapped emulator, inventory ROMs, plan saves, then carry
  out the actions (see `docs/sync-protocol.md`).
- `HashCache`: file hashes keyed by path, size and modified time (a small JSON file), so unchanged
  files aren't re-read every run.
- `RomDownloader` / `DownloadQueue`: resumable ROM downloads into the mapped ROM folder,
  unpacking multi-file zips.
- `LibraryModel`: the grouped, sorted, searchable library both apps show.

## Android app

1. **Pair**: enter the server address and the pairing code from the web GUI's Devices page.
2. **Platforms**: for each platform the server enables, add one or more emulators:
   - pick an emulator profile (e.g. 3DS → Azahar);
   - pick the save folder (for Azahar, the folder that contains `Nintendo 3DS`) with the system
     folder picker; the app keeps the permission and checks the folder looks right;
   - optionally pick the ROM folder.
3. **Sync**: a periodic WorkManager job (every 15 minutes, when online), a sync when the app
   opens, and **Sync now**. Conflicts are kept on both sides and resolved on the server's
   Conflicts page.
4. **Library**: browse the server's games with covers, open a game page, and download ROMs into
   the mapped ROM folder (a foreground download that resumes).

Known constraint: on Android 11+ the app cannot read `Android/data/<other app>`. Most emulators
can move their data to a user folder (Azahar, Dolphin, RetroArch, PPSSPP, DuckStation,
NetherSX2). Shizuku support could cover the rest later.

## Windows app

`desktop/` is the same app on Compose Desktop (it also builds on macOS and Linux, but only the
Windows installer is packaged so far). Folders are plain paths: the setup card opens the chooser
at the profile's `defaultPaths.windows` and offers any of those folders that exist. The library,
game pages and sync engine come from `shared/`.

- It lives in the tray (status, Sync now, Open, Quit); closing the window keeps it running, and
  starting it again brings the window back. `--hidden` starts it in the tray.
- It syncs at start, every 15 minutes, and 8 seconds after a watched save folder changes
  (`WatchService`, limited to the folders a profile can keep saves in).
- Settings, the hash cache and cached covers live in `%LOCALAPPDATA%\RomSafe`. The device token is
  sealed with DPAPI, so only the same Windows user can read it.

## Building

`clients/` is one Gradle build (Kotlin DSL, version catalog in `gradle/libs.versions.toml`). It
needs JDK 21 and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `clients/local.properties`).

```sh
cd clients
./gradlew :shared:allTests            # engine tests (JVM and Android host)
./gradlew :android:assembleDebug      # android/build/outputs/apk/debug/android-debug.apk
adb install -r android/build/outputs/apk/debug/android-debug.apk
./gradlew :desktop:run                # the Windows app; :desktop:packageMsi builds an installer
```

The server usually runs plain HTTP on the LAN. The app allows cleartext only to hosts that resolve
to private, loopback or Tailscale (100.64.0.0/10) addresses; anything else needs `https://`.
`docs/sync-protocol.md` is the API contract.
