import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { toolingTests } from './node-tests.mjs';

test('tooling inventory discovers nested and newly added tests without enumerating application dependencies', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-node-inventory-'));
  try {
    for (const dir of ['ops', 'pre-push/ci', 'sonarqube', 'lib', 'frontend/node_modules']) fs.mkdirSync(path.join(root, dir), { recursive: true });
    for (const name of ['ops/new.test.mjs', 'pre-push/ci/new.test.mjs', 'frontend/node_modules/foreign.test.mjs']) fs.writeFileSync(path.join(root, name), '');
    assert.deepEqual(toolingTests(root), ['ops/new.test.mjs', 'pre-push/ci/new.test.mjs']);
    fs.writeFileSync(path.join(root, 'lib/added.test.mjs'), '');
    assert.equal(toolingTests(root).length, 3);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
