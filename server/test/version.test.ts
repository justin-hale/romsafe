import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, test } from 'node:test';
import { startServer, type TestDevice, type TestServer } from './helpers.js';
import { compareVersions, MIN_CLIENT_VERSION, PROTOCOL, SERVER_VERSION } from '../src/version.js';

// The version handshake: what the server reports, and how it turns away apps that are too old.

let srv: TestServer;
let thor: TestDevice;

before(async () => {
  srv = await startServer();
  thor = await srv.pairDevice('Thor');
});

after(async () => srv.close());

test('the server version comes from the root package.json', () => {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'package.json');
  assert.equal(SERVER_VERSION, JSON.parse(fs.readFileSync(root, 'utf8')).version);
  assert.notEqual(compareVersions(SERVER_VERSION, MIN_CLIENT_VERSION), null);
});

test('health and device config report the version, protocol and oldest supported app', async () => {
  const health = await srv.app.inject({ method: 'GET', url: '/api/health' });
  assert.equal(health.statusCode, 200);
  assert.deepEqual(health.json(), {
    ok: true,
    name: 'romsafe',
    version: SERVER_VERSION,
    protocol: PROTOCOL,
    minClientVersion: MIN_CLIENT_VERSION,
  });

  const config = await thor.call('GET', '/api/device/config');
  assert.equal(config.status, 200);
  assert.deepEqual(config.body.server, { version: SERVER_VERSION, protocol: PROTOCOL, minClientVersion: MIN_CLIENT_VERSION });
});

test('an app older than minClientVersion gets 426 with a message to show', async () => {
  const old = await srv.app.inject({
    method: 'GET',
    url: '/api/device/config',
    headers: { ...thor.headers, 'x-romsafe-client-version': '0.0.9' },
  });
  assert.equal(old.statusCode, 426);
  assert.match(old.json().error, /Update the app/);
  assert.equal(old.json().minClientVersion, MIN_CLIENT_VERSION);

  // Pairing is turned away too, before the code is used up.
  const { body: code } = await srv.admin('POST', '/api/devices/pairing-code');
  const pair = await srv.app.inject({
    method: 'POST',
    url: '/api/device/pair',
    headers: { 'x-romsafe-client-version': '0.0.9' },
    payload: { code: code.code, name: 'Old phone', kind: 'android' },
  });
  assert.equal(pair.statusCode, 426);
  const retry = await srv.app.inject({
    method: 'POST',
    url: '/api/device/pair',
    headers: { 'x-romsafe-client-version': MIN_CLIENT_VERSION },
    payload: { code: code.code, name: 'Old phone', kind: 'android' },
  });
  assert.equal(retry.statusCode, 200);
});

test('current, newer, dev and unlabelled apps are let through', async () => {
  for (const v of [MIN_CLIENT_VERSION, '9.0.0', `${MIN_CLIENT_VERSION}-dev.5`, 'garbage', undefined]) {
    const headers: Record<string, string> = { ...thor.headers };
    if (v) headers['x-romsafe-client-version'] = v;
    const res = await srv.app.inject({ method: 'GET', url: '/api/device/config', headers });
    assert.equal(res.statusCode, 200, `client ${v}`);
  }
});

test('compareVersions orders major.minor.patch and ignores suffixes', () => {
  assert.equal(compareVersions('0.1.0', '0.1.0'), 0);
  assert.ok(compareVersions('0.2.0', '0.1.9')! > 0);
  assert.ok(compareVersions('0.10.0', '0.9.0')! > 0);
  assert.ok(compareVersions('1.0', '1.0.1')! < 0);
  assert.equal(compareVersions('v0.1.0+512', '0.1.0-rc.1'), 0);
  assert.equal(compareVersions('nope', '0.1.0'), null);
});
