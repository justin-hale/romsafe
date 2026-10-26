# 🛡️ RomSafe

**Your ROM library and saves, kept safe and in sync on every device.**

RomSafe is a self-hosted ROM library and save-sync server. Think RomM's
library and metadata, plus Syncthing-style sync that understands emulators:

- **Library.** Scans your ROM folders, hashes every file, groups files into games, and scrapes
  metadata and box art (IGDB, SteamGridDB, and libretro thumbnails, which need no key).
- **Save backups.** Every save version is kept. The current version of each save is also written
  as a plain file into the save folder you choose, e.g. `~/saves/3ds/...`.
- **Per-device mapping.** On each phone, handheld or PC you pick, per platform, which emulator you
  use and the folder where it keeps saves. RomSafe translates between layouts. A save made in
  RetroArch on Android (`Game.srm`) lands in mGBA on Windows as `Game.sav`, and 3DS saves keep
  Azahar's `Nintendo 3DS/<id0>/<id1>/title/...` structure.
- **ROM tracking.** Devices report which ROMs they have. RomSafe matches them to server ROMs (by
  hash, file name or title) and lets you send missing ROMs to a device.
- **Safe conflicts.** If a save changed on both sides, RomSafe keeps both copies and asks you which
  one wins. Nothing is silently overwritten.

```
             ┌────────────────────────────────────────┐
             │  RomSafe server (Docker / LXC)         │
             │  web GUI · library scanner · scraper   │
             │  save store + history · sync API       │
             └────────▲───────────────────▲───────────┘
                      │ HTTPS + device token
          ┌───────────┴─────┐       ┌─────┴───────────┐
          │  Android app    │       │  Windows app    │
          │  folder picker  │       │  tray sync      │
          │  per platform   │       │  per platform   │
          └─────────────────┘       └─────────────────┘
```

## Status

| Piece | State |
| --- | --- |
| Server: library scan, hashing, 3DS title IDs, GameCube/Wii disc IDs | ✅ |
| Server: metadata and cover scraping | ✅ IGDB, SteamGridDB, libretro thumbnails |
| Server: save sync, version history, conflicts | ✅ with an end-to-end test suite |
| Server: ROM inventory matching and download queue | ✅ |
| Web GUI | ✅ library, game pages, saves, platforms, devices, conflicts, settings |
| Docker image | ✅ |
| Android app | ✅ pairing, per-emulator save folders, background sync, library, ROM downloads |
| Windows app | ✅ the same engine as a tray app that syncs when save folders change |
| macOS and Linux apps | 🛠 the desktop app builds there, but isn't packaged yet |

## Quick start

**Server** (Docker, amd64 or arm64):

```sh
mkdir romsafe && cd romsafe
curl -fsSLO https://raw.githubusercontent.com/justin-hale/romsafe/main/docker-compose.yml
# edit docker-compose.yml: your ROM and save folders, and the user/uid that owns them
mkdir -p data && sudo chown 1000:1000 data
docker compose up -d
```

Open `http://<server>:8080` and create an admin password. Then:

1. **Platforms**: for each system, set the ROM folder and the save folder as the container sees
   them, e.g. ROMs `/library/roms/3ds` and saves `/library/saves/3ds`.
2. **Library → Scan library**: indexes ROMs and imports any saves already in your save folders.
3. **Library → Scrape metadata**: fills in covers and descriptions. Add IGDB/SteamGridDB keys
   under **Settings** for more than libretro box art.
4. **Devices → Pair a device**: shows a code to enter in the app.

**Apps**: from the [latest release](https://github.com/justin-hale/romsafe/releases/latest),
`romsafe-<version>.apk` for Android ([Obtainium](https://github.com/ImranR98/Obtainium) keeps it
updated) and `RomSafe-<version>.msi` for Windows. Enter the server address and the code, then pick
an emulator and its save folder for each platform. The app syncs when it opens, in the background,
and from **Sync now**.

[docs/install.md](docs/install.md) has the details: volumes and permissions, updating, HTTPS,
the plain Node.js install for an LXC or VM, and configuration variables.

## How saves are identified

Each save is stored under a **game key** plus a **file name**:

| Platform type | Game key | Example |
| --- | --- | --- |
| Cartridge systems (GB/GBA/NES/SNES/N64/DS/Genesis…) | ROM file name without extension | `Metroid Fusion (USA)` + `battery.sav` |
| 3DS, Wii | Title ID (read from the ROM or disc header) | `00040000000a5e00` + `00000001/main` |
| PSP | `SAVEDATA` folder name | `ULUS10336DATA00` + `DATA.BIN` |
| GameCube, PS2, Dreamcast | Memory card | `USA/A` + `card.raw`, `Mcd001` + `card.ps2`, `A1` + `vmu.bin` |

**Emulator profiles** (`profiles/*.yaml`) map those keys to each emulator's file layout, and each
platform's **save layout** decides where the server writes its copy. When a device names a ROM
differently from the server, RomSafe uses the ROM match to translate the save name. See
[docs/emulator-profiles.md](docs/emulator-profiles.md).

## Development

```sh
npm install
npm run dev          # API on :8080 (tsx watch)
npm run dev:web      # web GUI on :5173, proxied to the API
npm test             # server unit + end-to-end sync tests
npm run typecheck
```

- `server/`: Fastify + TypeScript, SQLite via the built-in `node:sqlite`.
- `web/`: React + Vite + Tailwind.
- `profiles/`: emulator profiles.
- `docs/`: [install](docs/install.md), [architecture](docs/architecture.md), [sync protocol](docs/sync-protocol.md), [emulator profiles](docs/emulator-profiles.md).
- `clients/`: the Android and Windows apps on a shared Kotlin Multiplatform engine
  ([clients/README.md](clients/README.md)).

## License

RomSafe is free software under the [GNU Affero General Public License v3.0](LICENSE). You can use,
modify and share it; if you run a modified version as a network service, you must make your
modified source available to its users.
