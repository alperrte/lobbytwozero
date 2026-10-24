import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import { isolatedEnvironment, CookieJar, scrub, requireFreshJar, captureCommand } from './hardware-auth-e2e.mjs';

test('runtime fixture never inherits application credentials or ambient JVM/debug configuration', () => {
  const env = isolatedEnvironment({ POSTGRES_PASSWORD: 'disposable-fixture' }, {
    Path: 'trusted-path', JAVA_HOME: 'trusted-java', DEBUG: 'true', JAVA_TOOL_OPTIONS: 'untrusted',
    AUTH_JWT_PRIVATE_KEY: 'real-key-must-not-be-inherited', POSTGRES_PASSWORD: 'real-password', SONAR_TOKEN: 'real-token',
  });
  assert.deepEqual(env, { Path: 'trusted-path', JAVA_HOME: 'trusted-java', POSTGRES_PASSWORD: 'disposable-fixture' });
});

test('cookie fixture respects path boundaries so refresh credentials never reach Hardware', () => {
  const jar = new CookieJar(), headers = new Headers();
  headers.append('Set-Cookie', '__Host-ltz_access=fixture-access; Path=/; Secure; HttpOnly');
  headers.append('Set-Cookie', '__Secure-ltz_refresh=fixture-refresh; Path=/api/v1/auth/session; Secure; HttpOnly');
  jar.accept(headers);
  assert.equal(jar.header('/api/v1/hardware/profiles'), '__Host-ltz_access=fixture-access');
  assert.match(jar.header('/api/v1/auth/session/refresh'), /fixture-refresh/);
  assert.doesNotMatch(jar.header('/api/v1/auth/session-other'), /fixture-refresh/);
});

test('logout removes cleared cookies while preserving unrelated CSRF state', () => {
  const jar = new CookieJar();
  const first = new Headers();
  first.append('Set-Cookie', '__Host-ltz_access=fixture-access; Path=/');
  first.append('Set-Cookie', '__Host-XSRF-TOKEN=fixture-csrf; Path=/');
  jar.accept(first);
  const clear = new Headers(); clear.append('Set-Cookie', '__Host-ltz_access=; Path=/; Max-Age=0'); jar.accept(clear);
  assert.equal(jar.get('__Host-ltz_access'), undefined);
  assert.equal(jar.header('/api/v1/hardware/profiles'), '__Host-XSRF-TOKEN=fixture-csrf');
});

test('failure diagnostics redact generated credentials and dynamically observed JWTs', () => {
  const text = scrub('private-fixture eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.signature fixture-pass', ['private-fixture', 'fixture-pass']);
  assert.equal(text, '[REDACTED] [JWT REDACTED] [REDACTED]');
});

test('runtime evidence rejects an absent or stale production artifact', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-hardware-runtime-'));
  try {
    const base = path.join(root, 'backend/services/hardware-service');
    fs.mkdirSync(path.join(base, 'src/main'), { recursive: true });
    fs.mkdirSync(path.join(base, 'target'));
    const parentPom = path.join(root, 'backend/pom.xml');
    const servicePom = path.join(base, 'pom.xml');
    const source = path.join(base, 'src/main/application.properties');
    for (const file of [parentPom, servicePom, source]) fs.writeFileSync(file, 'fixture');
    assert.throws(() => requireFreshJar(root, 'hardware-service'), /Build hardware-service/);
    const jar = path.join(base, 'target/hardware-service-0.0.1-SNAPSHOT.jar');
    fs.writeFileSync(jar, 'fixture artifact');
    assert.equal(requireFreshJar(root, 'hardware-service'), jar);
    const future = new Date(Date.now() + 10000); fs.utimesSync(source, future, future);
    assert.throws(() => requireFreshJar(root, 'hardware-service'), /Rebuild stale/);
  } finally {
    const target = fs.realpathSync(root), parent = fs.realpathSync(os.tmpdir());
    if (path.dirname(target) !== parent || !path.basename(target).startsWith('ltz-hardware-runtime-')) {
      throw new Error('Refusing cleanup outside the dedicated temporary fixture');
    }
    fs.rmSync(target, { recursive: true });
  }
});

function fixtureProcess() {
  const child = new EventEmitter(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
  return child;
}

test('Docker image pull progress on stderr cannot corrupt a successful container ID', async () => {
  const child = fixtureProcess(), result = captureCommand(child, 'docker');
  child.stderr.write('Unable to find image locally; pulling disposable image\n');
  child.stdout.write('a'.repeat(64) + '\n'); child.emit('close', 0);
  assert.equal(await result, 'a'.repeat(64));
});

test('a failed process exposes only redacted and bounded diagnostics', async () => {
  const child = fixtureProcess(), result = captureCommand(child, 'docker', ['fixture-password']);
  child.stderr.write('fixture-password'); child.emit('close', 1);
  await assert.rejects(result, error => error.message === 'docker failed: [REDACTED]');
});
