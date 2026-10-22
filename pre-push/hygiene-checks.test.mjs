import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { findSecretAssignments, nonLoopbackPorts, checkRepositoryHygiene } from './hygiene-checks.mjs';

const REAL_LOOKING = 'Zk3pQ9vLm2XrT7wB1nHs';

test('secret assignments: long real-looking values are found, placeholders and short values are not, values never returned', () => {
  const output = [
    `.env.sample:3:POSTGRES_PASSWORD=${REAL_LOOKING}`,
    'docs/a.md:10:AUTH_OTP_HMAC_KEY=CHANGE_ME_before_first_run_please',
    'docs/b.md:2:SONAR_TOKEN=',
    'docs/c.md:4:POSTGRES_PASSWORD=short',
    'docs/d.md:5:GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET_VALUE_FROM_ENV}',
    `src/e.txt:7:export AUTH_TOTP_ENCRYPTION_KEY=${REAL_LOOKING}`,
    'not a grep line',
  ].join('\n');
  const findings = findSecretAssignments(output);
  assert.deepEqual(findings.map(item => `${item.file}:${item.line}:${item.name}`), [
    '.env.sample:3:POSTGRES_PASSWORD',
    'src/e.txt:7:AUTH_TOTP_ENCRYPTION_KEY',
  ]);
  assert.ok(!JSON.stringify(findings).includes(REAL_LOOKING));
});

test('compose ports: only loopback bindings are accepted', () => {
  const compose = [
    'services:',
    '  db:',
    '    ports:',
    '      - "127.0.0.1:${POSTGRES_HOST_PORT:-5542}:5432"',
    '  web:',
    '    ports:',
    '      - "8080:8080"',
    '      - 0.0.0.0:9000:9000',
    '      - "9090"',
    '    environment:',
    '      PORT: "7000:7000"',
    '  api:',
    '    ports:',
    '      - "127.0.0.1:5080:8080"',
  ].join('\n');
  assert.deepEqual(nonLoopbackPorts(compose), ['8080:8080', '0.0.0.0:9000:9000']);
  assert.deepEqual(nonLoopbackPorts('services:\n  a:\n    ports:\n      - "127.0.0.1:1:1"\n'), []);
});

function fixtureRoot({ compose = true, dockerignore = '.env\n.env.*\n' } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-hygiene-'));
  if (compose) fs.writeFileSync(path.join(root, 'docker-compose.yml'), 'services:\n  a:\n    ports:\n      - "127.0.0.1:1:1"\n');
  if (dockerignore !== null) fs.writeFileSync(path.join(root, '.dockerignore'), dockerignore);
  return root;
}

function runner({ keys = '', assignments = '', notIgnored = [], gitleaks = 'absent', tooling = 0 } = {}) {
  const calls = [];
  const run = async (command, args) => {
    calls.push([command, ...args].join(' '));
    if (command === 'git' && args[0] === 'grep') return { code: 0, output: args.includes('-l') ? keys : assignments };
    if (command === 'git' && args[0] === 'check-ignore') return { code: notIgnored.includes(args.at(-1)) ? 1 : 0, output: '' };
    if (command === 'gitleaks') {
      if (gitleaks === 'absent') throw new Error('spawn gitleaks ENOENT');
      return { code: args[0] === 'version' ? 0 : gitleaks === 'leaks' ? 1 : 0, output: '' };
    }
    return { code: tooling, output: '' };
  };
  return { run, calls };
}

async function check(options, runnerOptions) {
  const root = fixtureRoot(options);
  const lines = [];
  const { run, calls } = runner(runnerOptions);
  try {
    const outcome = await checkRepositoryHygiene({ run, log: line => lines.push(line), root }).then(() => 'pass', error => error.message);
    return { outcome, lines, calls };
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
}

test('clean repository passes and a missing gitleaks is skipped, not failed', async () => {
  const result = await check({}, {});
  assert.equal(result.outcome, 'pass');
  assert.ok(result.lines.some(line => line.startsWith('[SKIPPED] gitleaks')));
  assert.ok(result.calls.some(call => call.startsWith('git check-ignore -q .env.dev')));
});

test('private key material, secret values and un-ignored ENV files each fail the check without printing values', async () => {
  const key = await check({}, { keys: 'backend/src/Leak.java\n' });
  assert.match(key.outcome, /private key material/);
  assert.ok(key.lines.some(line => line.includes('[PRIVATE KEY]') && line.includes('Leak.java')));

  const secret = await check({}, { assignments: `docs/x.md:4:POSTGRES_PASSWORD=${REAL_LOOKING}\n` });
  assert.match(secret.outcome, /secret-looking value/);
  assert.ok(secret.lines.every(line => !line.includes(REAL_LOOKING)));

  const ignored = await check({}, { notIgnored: ['.env.dev'] });
  assert.match(ignored.outcome, /\.env\.dev is not ignored/);
});

test('exposed compose ports and a missing .dockerignore fail', async () => {
  const root = fixtureRoot();
  fs.writeFileSync(path.join(root, 'docker-compose.yml'), 'services:\n  a:\n    ports:\n      - "5432:5432"\n');
  try {
    const { run } = runner();
    await assert.rejects(checkRepositoryHygiene({ run, log: () => {}, root }), /not bound to 127\.0\.0\.1/);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }

  const withoutIgnore = await check({ dockerignore: null }, {});
  assert.match(withoutIgnore.outcome, /\.dockerignore does not exclude/);
  const weakIgnore = await check({ dockerignore: 'node_modules\n' }, {});
  assert.match(weakIgnore.outcome, /\.dockerignore does not exclude/);
});

test('gitleaks findings fail the check; the key generator guard test failing also fails it', async () => {
  const leaks = await check({}, { gitleaks: 'leaks' });
  assert.match(leaks.outcome, /gitleaks reported leaks/);

  const root = fixtureRoot();
  fs.mkdirSync(path.join(root, 'ops'));
  fs.writeFileSync(path.join(root, 'ops/generate-auth-dev-keys.test.mjs'), '');
  try {
    const { run } = runner({ tooling: 1 });
    await assert.rejects(checkRepositoryHygiene({ run, log: () => {}, root }), /key generator guard test failed/);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
