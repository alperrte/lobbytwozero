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
        const missed = options.lowCoverage || (options.unbalancedCoverage && module === modules[0]) ? 100 : 0;
        const covered = options.unbalancedCoverage && module === modules[1] ? 10000 : 100;
        write(`${base}/site/jacoco/jacoco.xml`, `<report><counter type="LINE" missed="${missed}" covered="${covered}"/></report>`);
        write(`${base}/site/jacoco/index.html`, 'report');
        write(`${base}/classes/App.class`, 'bytecode');
      }
      return { code: options.buildFail ? 1 : 0, output: '' };
    }
    if (command === 'docker') {
      if (options.noDocker) return { code: 1, output: '' };
      if (args.includes('--services')) return { code: 0, output: options.deployed ? 'postgres\nauth-service\n' : 'postgres\n' };
      if (args.includes('--format') && args.includes('config')) return { code: 0, output: JSON.stringify({ name: 'ltz-dev', services: { 'auth-service': { ports: [{ target: 8080, published: '5081' }], environment: {} } } }) };
      if (args.includes('ps')) return { code: 0, output: '[]' }; // configured, but stopped
      return { code: 0, output: '' };
    }
    throw new Error('Unexpected command ' + command);
  };
  try {
    const result = await runQualityChecks({ root, profile: 'dev', ambient: { SONAR_TOKEN: 'private' }, runCommand,
      secretScan: ({ mode }) => { assert.equal(mode, 'worktree'); if (options.scannerUnavailable) throw new Error('Pinned scanner unavailable'); return options.scannerFinding ? 1 : 0; },
      prepare: async () => 'synthetic-context', coverageCheck: () => {
        if (options.lowCoverage || options.unbalancedCoverage) throw new Error('Central policy rejected synthetic report');
        return modules.map(module => ({ module, changed: { status: 'NOT_APPLICABLE' } }));
      }, log: line => lines.push(line) });
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
  });
});

for (const option of ['secret', 'scannerUnavailable', 'scannerFinding', 'skipped', 'missingCoverage', 'lowCoverage', 'unbalancedCoverage', 'buildFail', 'noDocker', 'deployed']) {
  test(`full check ${option} cannot produce a false success`, async () => {
    await fixture({ [option]: true }, async ({ result, lines }) => {
      assert.equal(result.exitCode, 1);
      assert.ok(!lines.includes('\nSAFE TO COMMIT'));
      if (option === 'deployed') assert.equal(result.rows['Backend HTTP Health'], 'FAIL');
    });
  });
}

test('host Maven receives profile DB settings, never Sonar values, and the password is redacted', async () => {
  await fixture({}, async ({ calls }) => {
    const maven = calls.filter(call => call.command.startsWith('mvn') && call.args.includes('verify'));
    assert.equal(maven.length, 1);
    assert.equal(maven[0].env.POSTGRES_USER, 'fixture-user');
    assert.equal(maven[0].env.POSTGRES_PASSWORD, 'fixture-password');
    assert.equal(maven[0].env.POSTGRES_HOST, '127.0.0.1');
    assert.equal(maven[0].env.POSTGRES_PORT, '5542');
    assert.deepEqual(maven[0].secrets, ['fixture-password']);
    assert.ok(calls.filter(call => call.command === 'docker').every(call => call.env.POSTGRES_PASSWORD === undefined));
  });
});

test('missing profile only warns; DB-bound services must then fail on their own', async () => {
  await fixture({ noProfile: true }, async ({ calls, lines }) => {
    assert.ok(lines.some(line => line.startsWith('[WARN]') && line.includes('Missing .env.dev')));
    assert.ok(calls.some(call => call.args.includes('verify') && call.env.POSTGRES_PASSWORD === undefined));
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
