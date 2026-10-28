import { test } from 'node:test';
import assert from 'node:assert/strict';
import { runTrivy } from './formats.mjs';

const image = () => ({ SchemaVersion: 2, ArtifactName: 'example:1', ArtifactType: 'container_image',
  Results: [{ Target: 'example:1 (ubuntu 24.04)', Class: 'os-pkgs', Type: 'ubuntu' }] });
const config = () => ({ SchemaVersion: 2, ArtifactName: '/fixture', ArtifactType: 'filesystem',
  Results: [{ Target: 'Dockerfile', Class: 'config', Type: 'dockerfile',
    MisconfSummary: { Successes: 4, Failures: 0 },
    Misconfigurations: [{ ID: 'AVD-DS-0001', Status: 'PASS', Severity: 'HIGH' }] }] });
const run = (report, mode = 'image', status = 0, options) => runTrivy(
  mode === 'image' ? ['image', 'example:1'] : ['config', '/fixture'],
  () => ({ status, stdout: JSON.stringify(report) }), () => {}, options);

test('Trivy rejects missing/wrong artifact identity and incomplete targets instead of CLEAN', () => {
  for (const report of [{ Results: [{}] }, { ...image(), Results: [{}] },
    { ...image(), ArtifactName: 'unrelated:1' }, { ...image(), ArtifactType: 'filesystem' },
    { ...image(), SchemaVersion: 99 }, { ...image(), Results: [null] },
    { ...image(), Results: [{ Target: 'unrelated', Class: 'os-pkgs', Type: 'ubuntu' }] }]) {
    assert.throws(() => run(report), error => error.category === 'SCANNER_ERROR');
  }
  assert.equal(run(image()).status, 'CLEAN');
});

test('Trivy config requires the complete expected Dockerfile inventory and performed checks', () => {
  const options = { expectedTargets: ['Dockerfile'] };
  assert.doesNotThrow(() => run(config(), 'config', 0, options));
  for (const report of [{ ...config(), Results: [] },
    { ...config(), Results: [{ Target: 'Dockerfile', Class: 'config', Type: 'dockerfile' }] },
    { ...config(), Results: [{ ...config().Results[0], Target: 'unrelated/Dockerfile' }] },
    { ...config(), Results: [...config().Results, ...config().Results] }]) {
    assert.throws(() => run(report, 'config', 0, options));
  }
  assert.throws(() => run(config(), 'config', 0, { expectedTargets: ['Dockerfile', 'other/Dockerfile'] }));
});

test('Trivy rejects malformed finding collections and nonzero-without-findings', () => {
  assert.throws(() => run({ ...image(), Results: [{ ...image().Results[0], Vulnerabilities: {} }] }));
  assert.throws(() => run(image(), 'image', 1), error => error.stage === 'exit-without-findings');
});

test('Trivy environment cannot silently ignore vulnerabilities or skip targets', () => {
  const previous = process.env.TRIVY_IGNORE_UNFIXED;
  process.env.TRIVY_IGNORE_UNFIXED = 'true';
  try {
    runTrivy(['image', 'example:1'], (_command, _args, options) => {
      assert.ok(Object.keys(options.env).every(key => !/^TRIVY_/i.test(key)));
      return { status: 0, stdout: JSON.stringify(image()) };
    }, () => {});
  } finally {
    if (previous === undefined) delete process.env.TRIVY_IGNORE_UNFIXED;
    else process.env.TRIVY_IGNORE_UNFIXED = previous;
  }
});
