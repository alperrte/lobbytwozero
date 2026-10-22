import { test } from 'node:test';
import assert from 'node:assert/strict';
import { scanSecrets } from './secrets.mjs';
import { selectScope, gatePassed } from './changes.mjs';
import { verifyArchive, extractExecutable, tools } from './install-tool.mjs';
import { createHash } from 'node:crypto';

test('unknown, renamed/deleted, parent and automation paths choose broad scope; product docs avoid Maven', () => {
  const available = ['services/game-service', 'services/library-service', 'services/api-gateway'];
  const select = files => selectScope(files, available);
  assert.deepEqual(select(['docs/product-guide.md', 'README.md']), { backend: false, frontend: false, config: false, modules: [] });
  assert.deepEqual(select(['backend/pom.xml']).modules, available);
  assert.equal(select(['frontend/package-lock.json']).frontend, true);
  for (const file of ['unknown.ext', '.github/workflows/ci.yml', 'pre-push/ci/secrets.mjs', '.pre-commit-config.yaml', 'AGENTS.md', 'CLAUDE.md', 'docs/SECURITY.md']) {
    assert.deepEqual(select([file]), { backend: true, frontend: true, config: true, modules: available });
  }
  assert.deepEqual(select(['backend/services/game-service/src/test/java/GameTest.java']).modules, ['services/game-service']);
  assert.deepEqual(select(['backend/services/game-service/src/main/java/com/ltz/game/GameServiceApplication.java']).modules, ['services/game-service']);
  assert.deepEqual(select(['backend/services/game-service/src/test/java/GameTest.java', 'backend/services/library-service/src/test/java/LibraryTest.java']).modules, available.slice(0, 2));
  for (const file of ['backend/services/removed-service/pom.xml', 'backend/config/pmd.xml', 'backend/services/game-service/pom.xml', 'backend/services/game-service/src/main/resources/db/migration/V1__fixture.sql', 'backend/services/game-service/src/main/java/com/ltz/game/api/GameController.java', 'backend/services/api-gateway/src/main/resources/application.properties']) {
    assert.deepEqual(select([file]).modules, available);
  }
});
test('aggregate gate fails closed on failure/cancel/missing/unexpected skip, allows only inapplicable skip', () => {
  const scope = { backend: true, frontend: false, config: false, modules: ['services/game-service'] };
  const results = { scope: 'success', secrets: 'success', node: 'success', hooks: 'success', backend: 'success', frontend: 'skipped', config: 'skipped' };
  assert.equal(gatePassed(scope, results), true);
  for (const key of ['scope', 'secrets', 'node', 'hooks', 'backend']) {
    for (const value of ['failure', 'cancelled', 'skipped', undefined]) assert.equal(gatePassed(scope, { ...results, [key]: value }), false);
  }
  assert.equal(gatePassed({}, results), false);
  assert.equal(gatePassed({ ...scope, modules: [] }, results), false);
  assert.equal(gatePassed({ ...scope, modules: ['../outside'] }, results), false);
  assert.equal(gatePassed(scope, { ...results, frontend: 'failure' }), false);
});
test('scanner range includes commit history, suppresses raw values, and cannot accept scanner errors', () => {
  const base = 'a'.repeat(40), head = 'b'.repeat(40);
  for (const code of [0, 10, 1, null]) {
    const calls = [], lines = [];
    const run = (cmd, args, opts) => {
      calls.push({ cmd, args, opts });
      if (cmd === 'git') return { status: 0, stdout: args.includes('--is-shallow-repository') ? 'false' : args.includes('ls-files') ? 'safe.js\0' : '' };
      if (args[0] === 'version') return { status: 0, stdout: tools.gitleaks.version };
      return { status: code, stdout: 'SYNTHETIC_SENSITIVE_OUTPUT', stderr: 'SYNTHETIC_SENSITIVE_OUTPUT' };
    };
    if ([0, 10].includes(code)) assert.equal(scanSecrets({ mode: 'range', base, head, run, log: x => lines.push(x) }), code === 0 ? 0 : 1);
    else assert.throws(() => scanSecrets({ mode: 'range', base, head, run, log: x => lines.push(x) }), /failed/);
    const args = calls.at(-1).args;
    assert.ok(args.includes('--log-opts=--full-history ' + base + '..' + head));
    assert.ok(args.includes('--redact=100'));
    assert.ok(args.includes('--ignore-gitleaks-allow'));
    assert.ok(!lines.join('\n').includes('SYNTHETIC_SENSITIVE_OUTPUT'));
  }
});
test('staged scanner uses index diff, rejects sensitive names before reading content and fails without binary', () => {
  const calls = [];
  const run = (cmd, args, opts) => {
    calls.push({ cmd, args, opts });
    if (cmd === 'git') return { status: 0, stdout: args.includes('ls-files') ? 'safe.js\0' : '+synthetic-added-line' };
    return args[0] === 'version' ? { status: 0, stdout: tools.gitleaks.version } : { status: 0 };
  };
  assert.equal(scanSecrets({ mode: 'staged', run, log: () => {} }), 0);
  assert.ok(calls.find(call => call.args[0] === 'diff').args.includes('--cached'));
  assert.equal(calls.at(-1).opts.input, '+synthetic-added-line');
  assert.throws(() => scanSecrets({ run: () => ({ status: 1, stdout: '' }), log: () => {} }), /filenames/);
  let count = 0;
  assert.equal(scanSecrets({ run: () => { count++; return { status: 0, stdout: '.env.dev\0' }; }, log: () => {} }), 1);
  assert.equal(count, 1);
  assert.throws(() => scanSecrets({ mode: 'range', base: '--all', head: 'a'.repeat(40), run }), /SHAs/);
});
test('pinned archive integrity rejects tampering before extraction', () => {
  const bytes = Buffer.from('synthetic archive');
  verifyArchive(bytes, createHash('sha256').update(bytes).digest('hex'));
  assert.throws(() => verifyArchive(bytes, '0'.repeat(64)), /mismatch/);
  assert.throws(() => verifyArchive(bytes, 'invalid'), /mismatch/);
});

test('Windows prek uses the hash-verified flat archive member; extraction errors retain a safe stage', () => {
  assert.equal(tools.prek.platforms['win32-x64'].binary, 'prek.exe');
  let call;
  extractExecutable('archive.zip', 'temporary', tools.prek.platforms['win32-x64'].binary, (...args) => { call = args; return { status: 0 }; });
  assert.deepEqual(call[1], ['-xf', 'archive.zip', '-C', 'temporary', 'prek.exe']);
  assert.equal(call[2].timeout, 60_000);
  assert.throws(() => extractExecutable('archive.zip', 'temporary', 'prek.exe', () => ({ status: 1 })),
    error => error.category === 'CONFIGURATION_ERROR' && error.stage === 'extraction');
  assert.throws(() => extractExecutable('archive.zip', 'temporary', 'prek.exe', () => ({ error: { code: 'ETIMEDOUT' } })),
    error => error.category === 'TIMEOUT');
});

test('Trivy distinguishes findings, database/registry failures, timeout and invalid JSON without raw logging', async () => {
  const { runTrivy } = await import('./formats.mjs');
  const raw = 'SYNTHETIC_PRIVATE_VALUE';
  for (const [result, category, stage] of [
    [{ status: 2, stderr: 'failed to download vulnerability DB ' + raw }, 'DEPENDENCY_ERROR', 'vulnerability-db'],
    [{ status: 1, stdout: '', stderr: 'failed to download vulnerability DB ' + raw }, 'DEPENDENCY_ERROR', 'vulnerability-db'],
    [{ status: 2, stderr: 'image pull failed ' + raw }, 'DEPENDENCY_ERROR', 'image-registry'],
    [{ status: null, error: { code: 'ETIMEDOUT' } }, 'TIMEOUT', 'timeout'],
    [{ status: 0, stdout: raw }, 'SCANNER_ERROR', 'json'],
    [{ status: 1, stdout: '{"Results":[{"Target":"image"}]}' }, 'SCANNER_ERROR', 'exit-without-findings'],
    [{ status: 1, stdout: JSON.stringify({ Results: [{ Target: 'image', Vulnerabilities: [{ VulnerabilityID: 'CVE-2026-1234', Severity: 'HIGH', PkgName: 'example', InstalledVersion: '1.0', FixedVersion: '1.1', Description: raw }] }] }) }, 'SECURITY_FINDINGS', 'findings'],
  ]) {
    const lines = [];
    assert.throws(() => runTrivy([], () => result, line => lines.push(line)), error => error.category === category && error.stage === stage);
    assert.ok(!lines.join('\n').includes(raw));
  }
});

test('full history refuses shallow repositories rather than reporting partial success', () => {
  assert.throws(() => scanSecrets({ mode: 'history', run: () => ({ status: 0, stdout: 'true' }), log: () => {} }), /Full history/);
});

test('isolated backend runner rejects wrong JDK before Docker and strips ambient credentials', async () => {
  const { backendCheck } = await import('./backend.mjs');
  let calls = 0;
  await assert.rejects(backendCheck({
    ambient: { POSTGRES_PASSWORD: 'ambient-value-must-not-propagate', SONAR_TOKEN: 'ambient-value-must-not-propagate' },
    runCommand: async (command, args, options) => {
      calls++;
      assert.equal(options.env.SONAR_TOKEN, undefined);
      assert.equal(options.env.POSTGRES_PASSWORD, 'ltz_test_only');
      assert.ok(command.startsWith('mvn'));
      assert.deepEqual(args, ['-version']);
      return { code: 0, output: 'Java version: 21.0.12' };
    },
    log: () => {},
  }), /JDK 25/);
  assert.equal(calls, 1);
});

test('partially started disposable PostgreSQL is cleaned up and never yields success', async () => {
  const { backendCheck } = await import('./backend.mjs');
  const calls = [];
  await assert.rejects(backendCheck({
    prepare: async () => 'synthetic-context',
    resetReports: () => {}, // no real reports in the synthetic startup driver
    ambient: {},
    runCommand: async (command, args) => {
      calls.push({ command, args });
      if (command.startsWith('mvn')) return { code: 0, output: 'Java version: 25.0.5' };
      return { code: args.includes('up') ? 1 : 0, output: '' };
    },
    log: () => {},
  }), /startup failed/);
  assert.equal(calls.length, 3);
  const up = calls.find(call => call.args.includes('up')).args;
  const down = calls.find(call => call.args.includes('down')).args;
  assert.equal(up[up.indexOf('-p') + 1], down[down.indexOf('-p') + 1]);
  assert.ok(/^ltz-test-[a-f0-9]+$/.test(up[up.indexOf('-p') + 1]));
  assert.ok(up.includes('pre-push/ci/compose.test.yml'));
  assert.ok(!up.includes('docker-compose.yml'));
});

test('source snapshot excludes ignored ENV, rejects listed sensitive files and unsafe cleanup', async () => {
  const { sourceSnapshot, removeSnapshot } = await import('./formats.mjs');
  const fs = await import('node:fs');
  const os = await import('node:os');
  const path = await import('node:path');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-fixture-'));
  let snapshot;
  try {
    fs.writeFileSync(path.join(root, 'safe.json'), '{}\n');
    fs.writeFileSync(path.join(root, '.env.dev'), 'SYNTHETIC_FIXTURE_ONLY=1\n');
    snapshot = sourceSnapshot(root, () => ({ status: 0, stdout: 'safe.json\0' }));
    assert.equal(fs.readFileSync(path.join(snapshot, 'safe.json'), 'utf8'), '{}\n');
    assert.equal(fs.existsSync(path.join(snapshot, '.env.dev')), false);
    assert.throws(() => sourceSnapshot(root, () => ({ status: 0, stdout: '.env.dev\0' })), /Sensitive/);
    assert.throws(() => sourceSnapshot(root, () => ({ status: 1, stdout: '' })), /enumerate/);
    assert.throws(() => removeSnapshot(os.tmpdir()), /Unsafe/);
  } finally {
    if (snapshot) removeSnapshot(snapshot);
    if (path.dirname(path.resolve(root)) !== path.resolve(os.tmpdir())) throw new Error('Unsafe fixture cleanup');
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('backend startup and cleanup failures are both preserved for diagnosis', async () => {
  const { backendCheck } = await import('./backend.mjs');
  for (const cleanupThrows of [false, true]) {
    await assert.rejects(backendCheck({
      prepare: async () => 'synthetic-context',
      resetReports: () => {}, // no real reports in the synthetic cleanup driver
      ambient: {},
      log: () => {},
      runCommand: async (command, args) => {
        if (command.startsWith('mvn')) return { code: 0, output: 'Java version: 25.0.4.1' };
        if (args.includes('down') && cleanupThrows) throw new Error('synthetic cleanup command failure');
        return { code: 1, output: '' };
      },
    }), error => {
      assert.ok(error instanceof AggregateError);
      assert.equal(error.errors.length, 2);
      assert.match(error.errors[0].message, /PostgreSQL startup failed/);
      assert.match(error.errors[1].message, /cleanup/);
      assert.match(error.message, /PostgreSQL startup failed/);
      assert.match(error.message, /cleanup/);
      return true;
    });
  }
});

test('Trivy metadata reporting never logs source excerpts and rejects malformed/empty reports', async () => {
  const { runTrivy } = await import('./formats.mjs');
  const lines = [];
  const secret = 'SYNTHETIC_PRIVATE_EXCERPT';
  const finding = { ID: 'AVD-DS-0001', Severity: 'HIGH', Title: secret, Description: secret,
    CauseMetadata: { StartLine: 12, Code: { Lines: [{ Content: secret }] } } };
  const report = { Results: [{ Target: 'Dockerfile', Misconfigurations: [finding] }] };
  assert.throws(() => runTrivy(['config', 'fixture'], () => ({ status: 1, stdout: JSON.stringify(report) }), line => lines.push(line)), /review/);
  assert.ok(lines.join('\n').includes('AVD-DS-0001'));
  assert.ok(!lines.join('\n').includes(secret));
  for (const response of [{ status: 0, stdout: secret }, { status: 0, stdout: '{"Results":[]}' }, { status: null, error: new Error(secret) }]) {
    assert.throws(() => runTrivy([], () => response, () => {}));
  }
  runTrivy([], () => ({ status: 0, stdout: '{"Results":[{"Target":"Dockerfile"}]}' }), () => {});
});

test('selected backend orchestration invokes only requested reactor modules and rejects invalid selections', async () => {
  const { backendCheck } = await import('./backend.mjs');
  const fs = await import('node:fs'), os = await import('node:os'), path = await import('node:path');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-selected-'));
  const selected = 'services/game-service';
  const write = (file, text) => { const target = path.join(root, file); fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, text); };
  write('backend/pom.xml', '<modules><module>services/game-service</module><module>services/library-service</module></modules>');
  const calls = [];
  const runCommand = async (command, args) => {
    calls.push({ command, args });
    if (args.includes('-version')) return { code: 0, output: 'Java version: 25.0.5' };
    if (command === 'docker') return { code: 0, output: args.includes('port') ? '127.0.0.1:32000\n' : '' };
    assert.equal(args[args.indexOf('-pl') + 1], selected);
    const target = 'backend/' + selected + '/target';
    write(target + '/surefire-reports/TEST-fixture.xml', '<testsuite tests="1" failures="0" errors="0" skipped="0"/>');
    write(target + '/site/jacoco/jacoco.xml', '<report><counter type="LINE" missed="1" covered="9"/></report>');
    write(target + '/site/jacoco/index.html', 'synthetic report');
    write(target + '/classes/Fixture.class', 'synthetic bytecode');
    return { code: 0, output: '' };
  };
  try {
    assert.equal(await backendCheck({ root, ambient: {}, modules: [selected], runCommand,
      prepare: async () => 'synthetic-context', coverageCheck: (_root, modules) => modules.map(module => ({module})), log: () => {} }), 0);
    assert.equal(calls.filter(call => call.args.includes('verify')).length, 1);
    const before = calls.length;
    for (const modules of [[], [selected, selected], ['../outside'], ['services/unknown']]) {
      await assert.rejects(backendCheck({ root, modules, runCommand, log: () => {} }), /Invalid selected/);
    }
    assert.equal(calls.length, before);
  } finally {
    if (path.dirname(path.resolve(root)) !== path.resolve(os.tmpdir())) throw new Error('Unsafe fixture cleanup');
    fs.rmSync(root, { recursive: true, force: true });
  }
});
