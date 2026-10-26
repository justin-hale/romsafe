# Installing RomSafe

RomSafe is a server you run at home plus an app on each device. Install the server first, then
pair each app with it. Use the same release version for the server and the apps: an app tells you
when it and the server are too far apart, and which one to update.

## Server: Docker Compose

The image is `ghcr.io/justin-hale/romsafe`, for amd64 and arm64. Tags: `latest`, each release
(`0.1.0`) and each minor line (`0.1`).

1. Make a folder for RomSafe and download the compose file into it:

   ```sh
   mkdir romsafe && cd romsafe
   curl -fsSLO https://raw.githubusercontent.com/justin-hale/romsafe/main/docker-compose.yml
   ```

2. Edit `docker-compose.yml`:
   - `volumes`: point `/library/roms` and `/library/saves` at your ROM and save folders. RomSafe
     writes to both: ROMs you upload in the web GUI, and the current copy of every save.
   - `user`: the uid:gid that owns those folders (`id -u` and `id -g` on the host).
   - `TZ`: your time zone.

3. Create the data folder as that user, so the container can write to it, then start it:

   ```sh
   mkdir -p data && sudo chown 1000:1000 data   # your uid:gid from step 2
   docker compose up -d
   ```

4. Open `http://<server>:8080` and choose the admin password (the first-run screen). To set it
   in the compose file instead, use `ROMSAFE_ADMIN_PASSWORD`.

5. In the web GUI:
   - **Platforms**: for each system, set the ROM folder and the save folder as the container sees
     them, e.g. `/library/roms/gba` and `/library/saves/gba`.
   - **Library → Scan library**, then **Scrape metadata** (add IGDB or SteamGridDB keys under
     **Settings** for more than libretro box art).
   - **Devices → Pair a device** shows the code for the app.

`./data` holds the database, every save version and the cover cache: back it up.

To update: `docker compose pull && docker compose up -d`. To build from a checkout instead, swap
`image:` for the commented `build: .` line.

If devices reach the server over the internet, put it behind a reverse proxy with HTTPS (Caddy,
Traefik, Nginx Proxy Manager). Device tokens are bearer tokens. The apps only allow plain HTTP to
LAN, loopback and Tailscale addresses.

## Android

1. Download `romsafe-<version>.apk` from the
   [latest release](https://github.com/justin-hale/romsafe/releases/latest) and open it. Android
   asks once to allow installs from your browser or file manager.
2. Open RomSafe, enter the server address (e.g. `192.168.1.20:8080`) and the pairing code.
3. On **Sync**, add an emulator for each platform and pick its save folder (and, optionally, its
   ROM folder for downloads). The card warns when a folder doesn't look like that emulator's.

For updates, add `https://github.com/justin-hale/romsafe` to
[Obtainium](https://github.com/ImranR98/Obtainium); it picks up the APK from each release.

Every release APK is signed with the same key. To check a download, compare its signing
certificate (Obtainium or [AppVerifier](https://github.com/soupslurpr/AppVerifier) show it) with:

```
SHA-256: 3c99d0ce8d36f7ea57c95c9856092cb00420458771b2e0227908c02fda5ff31f
```

Android 11 and later keep apps out of `Android/data/<other app>`. If an emulator keeps its saves
there, move its data folder to shared storage in the emulator's settings first (Azahar, Dolphin,
RetroArch, PPSSPP, DuckStation and NetherSX2 all can).

## Windows

Download `RomSafe-<version>.msi` from the
[latest release](https://github.com/justin-hale/romsafe/releases/latest) and run it. It installs
for your user only (no admin prompt) and adds a Start menu entry. The installer isn't code-signed
yet, so SmartScreen may warn: **More info → Run anyway**.

RomSafe lives in the tray: closing the window keeps it syncing. It syncs at start, every 15
minutes, and a few seconds after a save changes. Installing a newer MSI upgrades in place.

macOS and Linux packages aren't published yet; the desktop app runs there from source
(`cd clients && ./gradlew :desktop:run`).

## Server: plain Node.js (LXC, VM or bare metal)

On Debian or Ubuntu (a Proxmox LXC works well), without Docker:

```sh
apt install -y git curl
curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && apt install -y nodejs
git clone https://github.com/justin-hale/romsafe /opt/romsafe
cd /opt/romsafe && git checkout v0.1.0 && npm ci && npm run build
mkdir -p /var/lib/romsafe
```

Run it as a service, `/etc/systemd/system/romsafe.service`:

```ini
[Unit]
Description=RomSafe
After=network-online.target

[Service]
WorkingDirectory=/opt/romsafe
Environment=ROMSAFE_DATA_DIR=/var/lib/romsafe
ExecStart=/usr/bin/node server/dist/index.js
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```sh
systemctl daemon-reload && systemctl enable --now romsafe
```

Then continue from step 4 of the Docker guide. In an LXC, bind-mount your ROM and save folders
into the container and use those paths in **Platforms**.

To update: `git fetch --tags && git checkout v<new version> && npm ci && npm run build && systemctl restart romsafe`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `ROMSAFE_PORT` | `8080` | HTTP port |
| `ROMSAFE_HOST` | `0.0.0.0` | Address to listen on |
| `ROMSAFE_DATA_DIR` | `./data` (`/data` in Docker) | Database, save history blobs, covers |
| `ROMSAFE_ADMIN_PASSWORD` | unset | Fixed admin password (otherwise set on first run) |
| `ROMSAFE_PROFILES_DIR` | `./profiles` | Emulator profile YAML files |
