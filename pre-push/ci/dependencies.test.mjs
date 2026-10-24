import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { dependencyCheck } from './dependencies.mjs';
import { runTrivy } from './formats.mjs';

function fixture(action) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-sca-test-'));
  const write = (name, data) => { const file = path.join(root, name); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, data); return file; };
  write('backend/pom.xml', '<modules><module>services/auth-service</module><module>services/api-gateway</module></modules>');
  const jar = write('backend/services/auth-service/target/auth.jar', Buffer.from([0x50, 0x4b, 0x03, 0x04]));
  write('frontend/package-lock.json', JSON.stringify({ lockfileVersion: 3, packages: { '': {}, 'node_modules/example': { version: '1.0' } } }));
  try { action({ root, write, jar }); } finally { fs.rmSync(root, { recursive: true, force: true }); }
}

test('SCA scans only fresh requested archives and lockfile, validates target inventory and cleans staging on failure', () => fixture(({ root }) => {
  let staging;
  const run = (_command, args) => {
    if (args[0] === '--list') return { status: 0, stdout: 'BOOT-INF/lib/example.jar\n' };
    staging = args[args.indexOf('--exit-code') + 2];
    assert.deepEqual(fs.readdirSync(staging).sort(), ['frontend', 'services']);
    return { status: 0, stdout: JSON.stringify({ SchemaVersion: 2, ArtifactName: staging, ArtifactType: 'filesystem', Results: [
      { Target: 'Java', Class: 'lang-pkgs', Type: 'jar', Packages: [{ Name: 'example', Version: '1', FilePath: 'services/auth-service/app.jar/BOOT-INF/lib/example.jar' }] },
      { Target: 'frontend/package-lock.json', Class: 'lang-pkgs', Type: 'npm', Packages: [{ Name: 'example', Version: '1' }] },
    ] }) };
  };
  assert.equal(dependencyCheck({ root, modules: ['services/auth-service'], frontend: true, since: Date.now(), run, log: () => {} }), 0);
  assert.equal(fs.existsSync(staging), false);
  assert.throws(() => dependencyCheck({ root, frontend: true, scan: args => { staging = args.at(-1); throw new Error('offline'); }, log: () => {} }), /offline/);
  assert.equal(fs.existsSync(staging), false);
}));

test('SCA rejects stale, missing, ambiguous, invalid archives and invalid scopes before claiming success', () => fixture(({ root, write, jar }) => {
  const options = { root, modules: ['services/auth-service'], since: Date.now(), scan: () => assert.fail('must not scan'), log: () => {} };
  for (const modules of [[], ['services/missing'], ['../outside'], ['services/auth-service', 'services/auth-service']]) assert.throws(() => dependencyCheck({ ...options, modules }));
  assert.throws(() => dependencyCheck({ ...options, since: undefined }));
  assert.throws(() => dependencyCheck({ ...options, modules: ['services/api-gateway'] }));
  fs.utimesSync(jar, 1, 1);
  assert.throws(() => dependencyCheck(options), /Stale/);
  fs.writeFileSync(jar, 'not a zip');
  assert.throws(() => dependencyCheck(options), /Invalid application archive/);
  write('backend/services/auth-service/target/second.jar', 'ambiguous');
  assert.throws(() => dependencyCheck(options), /Exactly one/);
}));

test('SCA parser rejects empty/unrelated package targets, omitted archives, null packages, findings and scanner errors', () => {
  const args = ['fs', '/fixture'];
  const expectedArtifacts = [{ path: 'services/auth-service/app.jar', kind: 'jar' }];
  const result = { Target: 'Java', Class: 'lang-pkgs', Type: 'jar', Packages: [{ FilePath: 'unrelated.jar' }] };
  const report = Results => ({ SchemaVersion: 2, ArtifactName: '/fixture', ArtifactType: 'filesystem', Results });
  for (const results of [[], [result], [{ ...result, Packages: [] }], [{ ...result, Packages: [null] }]]) {
    assert.throws(() => runTrivy(args, () => ({ status: 0, stdout: JSON.stringify(report(results)) }), () => {}, { expectedArtifacts }));
  }
  result.Packages = [{ FilePath: expectedArtifacts[0].path }];
  result.Vulnerabilities = [{ VulnerabilityID: 'CVE-2026-12345', Severity: 'HIGH', PkgName: 'example', InstalledVersion: '1' }];
  assert.throws(() => runTrivy(args, () => ({ status: 1, stdout: JSON.stringify(report([result])) }), () => {}, { expectedArtifacts }), e => e.category === 'SECURITY_FINDINGS');
  assert.throws(() => runTrivy(args, () => ({ status: 2, stderr: 'Java DB download failed' }), () => {}, { expectedArtifacts }), e => e.category === 'DEPENDENCY_ERROR');
});

test('SCA refuses thin archives and a report that omits one packaged runtime library', () => fixture(({ root }) => {
  assert.throws(() => dependencyCheck({ root, modules: ['services/auth-service'], since: Date.now(),
    run: () => ({ status: 0, stdout: 'META-INF/MANIFEST.MF\n' }), log: () => {} }), /runtime libraries missing/);
  const expectedArtifacts = [{ path: 'services/auth-service/app.jar', kind: 'jar', entries: ['BOOT-INF/lib/a.jar', 'BOOT-INF/lib/b.jar'] }];
  const target = { Target: 'Java', Class: 'lang-pkgs', Type: 'jar', Packages: [{ FilePath: expectedArtifacts[0].path + '/BOOT-INF/lib/a.jar' }] };
  const invoke = () => runTrivy(['fs', '/fixture'], () => ({ status: 0, stdout: JSON.stringify({
    SchemaVersion: 2, ArtifactName: '/fixture', ArtifactType: 'filesystem', Results: [target],
  }) }), () => {}, { expectedArtifacts });
  assert.throws(invoke, error => error.stage === 'target-coverage');
  target.Packages.push({ FilePath: expectedArtifacts[0].path + '/BOOT-INF/lib/b.jar' });
  assert.doesNotThrow(invoke);
}));
