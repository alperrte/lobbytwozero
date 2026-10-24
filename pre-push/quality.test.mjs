import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { runQualityChecks } from './quality.mjs';
import { readModules, generatedPath, withReportLock } from '../lib/quality-tools.mjs';

async function fixture(options, action) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-quality-'));
  const write = (name, content) => {
    const filename = path.join(root, name);
    fs.mkdirSync(path.dirname(filename), { recursive: true });
    fs.writeFileSync(filename, content);
  };
  const modules = ['services/auth-service', 'services/api-gateway'];
  if (!options.noProfile) write('.env.dev', 'POSTGRES_USER=fixture-user\nPOSTGRES_PASSWORD=fixture-password\nPOSTGRES_HOST=postgres\nPOSTGRES_HOST_PORT=5542\nSONAR_TOKEN=profile-token\n');
  write('backend/pom.xml', modules.map(module => `<module>${module}</module>`).join(''));
  const calls = [], lines = [];
  const runCommand = async (command, args, settings) => {
    calls.push({ command, args, env: settings.env, secrets: settings.secrets });
    assert.equal(settings.env.SONAR_TOKEN, undefined);
    if (command === 'git') return { code: 0, output: options.secret && args.includes('ls-files') ? '.env.dev\0' : '' };
    if (command.startsWith('mvn')) {
      if (args.includes('-version')) return { code: 0, output: 'Java version: 25.0.4' };
      for (const module of modules) {
        const base = `backend/${module}/target`;
        write(`${base}/surefire-reports/TEST-app.xml`, `<testsuite tests="1" failures="0" errors="0" skipped="${options.skipped ? 1 : 0}"/>`);
        if (options.missingCoverage) continue;
        write(`${base}/site/jacoco/jacoco.xml`, `<report><counter type="LINE" missed="${options.lowCoverage ? 100 : 0}" covered="100"/></report>`);
        write(`${base}/site/jacoco/index.html`, 'report');
        write(`${base}/classes/App.class`, 'bytecode');
      }
      return { code: options.buildFail ? 1 : 0, output: '' };
    }
    if (command === 'docker') {
      if (options.noDocker) return { code: 1, output: '' };
      if (options.noProfile && args.includes('--format') && args.includes('config')) return { code: 1, output: '' };
      if (args.includes('--services')) return { code: 0, output: options.deployed ? 'postgres\nauth-service\n' : 'postgres\n' };
      if (args.includes('--format') && args.includes('config')) return { code: 0, output: JSON.stringify({ name: 'ltz-dev', services: {
        postgres: { ports: [{ target: 5432, published: '5542', host_ip: '127.0.0.1' }], environment: { POSTGRES_USER: 'fixture-user', POSTGRES_PASSWORD: 'fixture-db-pass' } },
        'auth-service': { ports: [{ target: 8080, published: '5081' }], environment: { DB_AUTH_NAME: 'auth_db' } }
      } }) };
      if (args.includes('ps')) return { code: 0, output: '[]' }; // configured, but stopped
      return { code: 0, output: '' };
    }
    throw new Error('Unexpected command ' + command);
  };
  try {
    const result = await runQualityChecks({ root, profile: 'dev', ambient: { SONAR_TOKEN: 'private', DEBUG: 'tool-namespace' }, runCommand, log: line => lines.push(line) });
    await action({ root, write, result, calls, lines });
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
}

test('full check matches the current backend and postgres scope without inventing frontend checks', async () => {
  await fixture({}, async ({ result, calls, lines }) => {
    assert.equal(result.exitCode, 0);
    assert.equal(result.rows['Backend HTTP Health'], 'SKIPPED');
    assert.ok(calls.every(call => !call.command.startsWith('npm')));
    assert.ok(calls.some(call => call.args.includes('.env.dev')));
    assert.ok(lines.includes('\nSAFE TO COMMIT'));
    const maven = calls.find(call => call.args.includes('verify'));
    assert.equal(maven.env.POSTGRES_PORT, '5542');
    assert.equal(maven.env.POSTGRES_PASSWORD, 'fixture-db-pass');
    assert.equal(maven.env.DB_AUTH_NAME, 'auth_db');
    assert.ok(calls.filter(call => call.command === 'docker').every(call => call.env.POSTGRES_PASSWORD === undefined));
    assert.ok(lines.every(line => !line.includes('fixture-db-pass')));
  });
});

for (const option of ['secret', 'skipped', 'missingCoverage', 'lowCoverage', 'buildFail', 'noDocker', 'deployed']) {
  test(`full check ${option} cannot produce a false success`, async () => {
    await fixture({ [option]: true }, async ({ result, lines }) => {
      assert.equal(result.exitCode, 1);
      assert.ok(!lines.includes('\nSAFE TO COMMIT'));
      if (option === 'deployed') assert.equal(result.rows['Backend HTTP Health'], 'FAIL');
    });
  });
}

test('host Maven receives resolved Compose DB settings, never Sonar values, and the password is redacted', async () => {
  await fixture({}, async ({ calls }) => {
    const maven = calls.filter(call => call.command.startsWith('mvn') && call.args.includes('verify'));
    assert.equal(maven.length, 1);
    assert.equal(maven[0].env.POSTGRES_USER, 'fixture-user');
    assert.equal(maven[0].env.POSTGRES_PASSWORD, 'fixture-db-pass');
    assert.equal(maven[0].env.POSTGRES_HOST, '127.0.0.1');
    assert.equal(maven[0].env.POSTGRES_PORT, '5542');
    assert.deepEqual(maven[0].secrets, ['fixture-db-pass']);
    assert.ok(calls.filter(call => call.command === 'docker').every(call => call.env.POSTGRES_PASSWORD === undefined));
  });
});

test('missing Compose profile fails before Maven can use fallback database settings', async () => {
  await fixture({ noProfile: true }, async ({ result, calls, lines }) => {
    assert.equal(result.exitCode, 1);
    assert.equal(result.rows['Backend Tests'], 'FAIL');
    assert.ok(lines.some(line => line.includes('Cannot resolve the selected Compose profile for backend tests.')));
    assert.ok(!calls.some(call => call.args.includes('verify')));
  });
});

test('module paths cannot escape the repository or repeat; generated output stays inside the repository', async () => {
  await fixture({}, async ({ root, write }) => {
    for (const value of ['../outside', 'services/auth-service</module><module>services/auth-service']) {
      write('backend/pom.xml', `<module>${value}</module>`);
      assert.throws(() => readModules(root));
    }
    assert.throws(() => generatedPath(root, '../outside'));
    await assert.rejects(withReportLock(root, async () => { throw new Error('fixture failure'); }), /fixture failure/);
    assert.equal(fs.existsSync(path.join(root, 'test-results/quality.lock')), false);
  });
});

test('ambient DEBUG never reaches the Maven test process', async () => {
  await fixture({}, async ({ calls }) => {
    const builds = calls.filter(call => call.command.startsWith('mvn') && call.args.includes('verify'));
    assert.equal(builds.length, 1);
    assert.equal(builds[0].env.DEBUG, undefined);
  });
});
