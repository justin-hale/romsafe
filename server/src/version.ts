import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// The root package.json is the one version source for a release: the server
// reads it here, and the client builds read it in Gradle.
const rootPackage = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'package.json');

export const SERVER_VERSION: string = (() => {
  try {
    return String(JSON.parse(fs.readFileSync(rootPackage, 'utf8')).version ?? '0.0.0');
  } catch {
    return '0.0.0';
  }
})();

/**
 * The device API generation (docs/sync-protocol.md, "Versions"). Bump it when
 * a change would break clients built for the previous one; clients refuse a
 * server whose protocol is older than the one they need.
 */
export const PROTOCOL = 1;

/** The oldest app release this server still works with. Raise it together with PROTOCOL. */
export const MIN_CLIENT_VERSION = '0.1.0';

/** The header apps send their own version in. */
export const CLIENT_VERSION_HEADER = 'x-romsafe-client-version';

export const versionInfo = () => ({ version: SERVER_VERSION, protocol: PROTOCOL, minClientVersion: MIN_CLIENT_VERSION });

/**
 * Compares `major.minor.patch` versions, ignoring any `-prerelease` or `+build`
 * suffix. Returns null when either side doesn't parse.
 */
export function compareVersions(a: string, b: string): number | null {
  const parse = (v: string) => {
    const m = /^v?(\d+)\.(\d+)(?:\.(\d+))?/.exec(v.trim());
    return m ? [Number(m[1]), Number(m[2]), Number(m[3] ?? 0)] : null;
  };
  const pa = parse(a);
  const pb = parse(b);
  if (!pa || !pb) return null;
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pa[i] - pb[i];
  return 0;
}

/** Why a client of [clientVersion] can't use this server, or null when it can (or didn't say). */
export function clientTooOld(clientVersion: string | undefined): string | null {
  if (!clientVersion) return null;
  const cmp = compareVersions(clientVersion, MIN_CLIENT_VERSION);
  if (cmp === null || cmp >= 0) return null;
  return `This app (${clientVersion}) is too old for this RomSafe server (${SERVER_VERSION}). Update the app to ${MIN_CLIENT_VERSION} or newer.`;
}
