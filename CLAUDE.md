# RomSafe

Self-hosted ROM library and save-sync server (think RomM + Syncthing for emulator saves), plus
Android and Windows clients. Read `README.md` and `docs/` first; this file is the working brief.

## Layout

- `server/`: Fastify + TypeScript on Node 24, SQLite through the built-in `node:sqlite`
  (`server/src/db.ts`, with migrations in the same file; append new ones, never edit old ones).
- `web/`: React 19 + Vite + Tailwind 4 admin GUI, served by the server from `web/dist`.
- `profiles/*.yaml`: emulator save layouts (`docs/emulator-profiles.md`).
- `clients/`: one Gradle build (`clients/README.md`). `shared/` is the Kotlin Multiplatform engine
  (API client, sync, ROM downloads, library model); `android/` is the Compose app; `desktop/` is
  the Compose Desktop tray app for Windows, macOS and Linux.
- `docs/sync-protocol.md`: the device API contract the clients implement.

## Commands

```sh
npm install
npm run dev        # API on :8080
npm run dev:web    # GUI on :5173, proxies /api to :8080
npm test           # server tests (node:test via tsx), including end-to-end sync scenarios
npm run typecheck
npm run build
```

Clients (JDK 21 and the Android SDK; run from `clients/`):

```sh
./gradlew :shared:allTests :desktop:test   # engine and desktop tests
./gradlew :android:assembleDebug           # debug APK, installed with adb install -r
./gradlew :desktop:run                     # packageMsi / packageDmg / packageAppImage on that OS
```

Run `npm run typecheck && npm test` before every commit, and the client tests when `clients/`
changed.

## Releasing

The root `package.json` version is the release version for the server and both apps (the
clients' Gradle build reads it). To release: bump it (and the `server`/`web` workspace versions to
match), commit, and push a `vX.Y.Z` tag. `.github/workflows/release.yml` then pushes the image to
`ghcr.io/justin-hale/romsafe`, and attaches the signed APK, the Windows MSI, the macOS DMGs (arm64
and x64, not notarized) and the Linux AppImage to a GitHub release.
It needs the `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD` repository secrets. An incompatible device API change also bumps the
protocol ("Versions" in `docs/sync-protocol.md`).

## Core concepts

- A save is `(platform, game_key, file)`. How the game key is formed depends on the platform's
  `keyKind` in `server/src/platforms.ts`: `rom` (ROM file stem), `titleId` (3DS/Wii), `saveDir`
  (PSP) or `card` (GameCube/PS2/Dreamcast memory cards).
- Profiles map device-relative paths to and from keys with path templates
  (`server/src/profiles/template.ts`). The server's own save folder uses the same templates (the
  platform "save layout").
- `server/src/saves/sync.ts` holds the sync decision table. `device_save_state` stores, per
  device and save, the version they last agreed on and the hash of the file *as it is on the
  device* (it can differ from the stored raw save when a profile has a `format`, e.g. DraStic `.dsv`).
- ROM-keyed saves are translated through the device's ROM matches (`server/src/roms/matching.ts`),
  so differently named ROMs still share saves.

## Owner's setup (for testing)

- Server target: a Proxmox host. Deploy RomSafe as an LXC container, either Docker-in-LXC with
  `docker-compose.yml` or plain Node 24 (see README). Ask the owner for the SSH host and user;
  never commit hostnames, IPs or credentials.
- Android test device: AYN Thor (Android handheld with two screens). Likely emulators: RetroArch,
  Azahar (3DS), DraStic/melonDS (DS), Dolphin (GC/Wii), DuckStation (PS1), NetherSX2/AetherSX2
  (PS2), Flycast (Dreamcast), mGBA/Pizza Boy (GB/GBC/GBA). Install builds with `adb`.
- Windows test machine: the owner's gaming PC (PCSX2, Dolphin, DuckStation, Azahar, RetroArch,
  melonDS, Flycast).
- Platforms that matter most: GBC, GBA, GameCube, Wii, DS, 3DS, PSX, PS2, Dreamcast.

## Roadmap

Done: the server runs on the owner's Proxmox LXC against their real library; the Android app
and the Windows app pair, sync saves, browse the library and download ROMs.

1. First public release (v0.1.0): Docker image on GHCR, signed APK and Windows installer on
   GitHub Releases.
2. A Proxmox community helper script, then platform templates (Unraid, TrueNAS and the like).
3. Library management from the apps (restore versions, upload saves, covers), behind a per-device
   permission.
4. macOS and Linux desktop packages: the release workflow builds DMGs and an AppImage; they still
   need testing on a Mac, a Steam Deck and a Linux desktop.
5. More profiles and formats as real setups need them (see "Known gaps" in
   `docs/emulator-profiles.md`).

## Conventions

- Keep the server dependency-light. Prefer Node built-ins.
- Every behavior change to sync needs a scenario in `server/test/sync.test.ts`.
- Comments explain *why*. Match the existing style.
