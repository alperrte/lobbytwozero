import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { startTestDatabase } from './test-database.mjs';
import { runBackendChecks } from './backend-check.mjs';

function dockerFake(options = {}) {
  const calls = [];
  const runCommand = async (command, args, settings) => {
    calls.push({ command, args, env: settings.env, secrets: settings.secrets });
    assert.equal(command, 'docker');
    if (args[0] === 'ps') return { code: 0, output: options.stale ? 'oldcontainer\n' : '' };
    if (args[0] === 'run') return { code: options.runFails ? 125 : 0, output: options.runFails ? 'Cannot connect to the Docker daemon' : 'containerid\n' };
    if (args[0] === 'port') return { code: 0, output: options.noPort ? '' : '127.0.0.1:53386\n[::]:53386\n' };
    if (args[0] === 'exec') return { code: 0, output: options.neverReady ? '' : '1\n' };
    return { code: 0, output: '' };
  };
  return { calls, runCommand };
}

const start = (fake, extra = {}) => startTestDatabase({
  root: path.join(os.tmpdir(), 'ltz-root'), env: { SONAR_TOKEN: undefined, ...extra.env }, runCommand: fake.runCommand,
  log: extra.log ?? (() => {}), attempts: 3, delayMs: 0, wait: async () => {}
});

test('starts a throwaway PostgreSQL and returns connection env without putting the password on the command line', async () => {
  const fake = dockerFake();
  const lines = [];
  const database = await start(fake, { log: line => lines.push(line) });
  const run = fake.calls.find(call => call.args[0] === 'run');
  assert.deepEqual(database.env.POSTGRES_HOST, 'localhost');
  assert.equal(database.env.POSTGRES_PORT, '53386');
  assert.equal(database.env.POSTGRES_USER, 'ltz_test');
  assert.match(database.env.POSTGRES_PASSWORD, /^[0-9a-f]{32}$/);
  assert.ok(!run.args.some(arg => arg.includes(database.env.POSTGRES_PASSWORD)), 'password must not be an argument');
  assert.equal(run.env.POSTGRES_PASSWORD, database.env.POSTGRES_PASSWORD);
  assert.ok(run.args.includes('POSTGRES_PASSWORD'));
  assert.ok(run.args.some(arg => arg.endsWith(':/docker-entrypoint-initdb.d:ro')), 'init scripts are mounted read-only');
  assert.ok(run.args.includes('127.0.0.1::5432'), 'port is bound to loopback only');
  assert.deepEqual(database.secrets, [database.env.POSTGRES_PASSWORD]);
  assert.ok(!lines.some(line => line.includes(database.env.POSTGRES_PASSWORD)));
  await database.stop();
  assert.ok(fake.calls.some(call => call.args[0] === 'rm' && call.args.includes('-f') && call.args.includes(run.args[run.args.indexOf('--name') + 1])));
});

test('removes leftover containers from interrupted runs', async () => {
  const fake = dockerFake({ stale: true });
  await start(fake);
  assert.ok(fake.calls.some(call => call.args[0] === 'rm' && call.args.includes('oldcontainer')));
});

test('does nothing when POSTGRES_PASSWORD is already provided or LTZ_TEST_DB=off', async () => {
  for (const env of [{ POSTGRES_PASSWORD: 'user-chosen' }, { LTZ_TEST_DB: 'off' }]) {
    const fake = dockerFake();
    const database = await start(fake, { env });
    assert.equal(fake.calls.length, 0);
    assert.deepEqual(database.env, {});
  }
});

for (const option of ['runFails', 'noPort', 'neverReady']) {
  test(`${option} warns, cleans up and falls back to the unchanged environment`, async () => {
    const fake = dockerFake({ [option]: true });
    const lines = [];
    const database = await start(fake, { log: line => lines.push(line) });
    assert.deepEqual(database.env, {});
    assert.ok(lines.some(line => line.startsWith('[WARN] Geçici test veritabanı başlatılamadı')));
    assert.ok(fake.calls.some(call => call.args[0] === 'rm'), 'partially started container is removed');
  });
}

test('backend checks pass the database env only to Maven, redact it, and always stop the database', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-db-'));
  try {
    fs.mkdirSync(path.join(root, 'backend'), { recursive: true });
    fs.writeFileSync(path.join(root, 'backend/pom.xml'), '<module>services/auth-service</module>');
    const calls = [];
    let stopped = 0;
    const runCommand = async (command, args, settings) => {
      calls.push({ command, args, settings });
      if (args.includes('-version')) return { code: 0, output: 'Java version: 25.0.4' };
      return { code: 1, output: '' }; // build fails
    };
    const testDatabase = async () => ({ env: { POSTGRES_PASSWORD: 'p' }, secrets: ['p'], stop: async () => { stopped++; } });
    const result = await runBackendChecks({ root, service: 'auth-service', ambient: { SONAR_TOKEN: 'x' }, runCommand, testDatabase, log: () => {} });
    const verify = calls.find(call => call.args.includes('verify'));
    assert.equal(result.exitCode, 1);
    assert.equal(verify.settings.env.POSTGRES_PASSWORD, 'p');
    assert.equal(verify.settings.env.SONAR_TOKEN, undefined);
    assert.deepEqual(verify.settings.secrets, ['p']);
    assert.equal(calls.find(call => call.args.includes('-version')).settings.env.POSTGRES_PASSWORD, undefined);
    assert.equal(stopped, 1);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
