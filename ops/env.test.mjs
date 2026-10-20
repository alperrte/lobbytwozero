import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const source = path.dirname(fileURLToPath(import.meta.url));

test('ENV switching preserves local edits, backs up forced changes and reports profile drift', { skip: process.platform !== 'win32' }, () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-env-'));
  fs.mkdirSync(path.join(root, 'ops'));
  fs.copyFileSync(path.join(source, 'env.ps1'), path.join(root, 'ops/env.ps1'));
  const run = (...args) => spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'ops/env.ps1'), ...args], { encoding: 'utf8', windowsHide: true });
  const active = path.join(root, '.env');
  try {
    fs.writeFileSync(path.join(root, '.env.dev'), 'APP_ENV=dev\n');
    fs.writeFileSync(path.join(root, '.env.prod'), 'APP_ENV=prod\n');
    assert.equal(run('status').status, 0);
    assert.equal(fs.existsSync(active), false);
    assert.equal(run('use', 'dev').status, 0);
    assert.equal(fs.readFileSync(active, 'utf8'), '# LTZ-ACTIVE-PROFILE=dev\nAPP_ENV=dev\n');
    assert.match(run('status').stdout, /matches/);
    fs.appendFileSync(active, 'LOCAL_EDIT=keep\n');
    assert.match(run('status').stdout, /differs/);
    assert.notEqual(run('use', 'prod').status, 0);
    assert.match(fs.readFileSync(active, 'utf8'), /LOCAL_EDIT=keep/);
    assert.equal(run('use', 'prod', '-Force').status, 0);
    assert.match(fs.readFileSync(path.join(root, '.env.bak'), 'utf8'), /LOCAL_EDIT=keep/);
    assert.match(fs.readFileSync(active, 'utf8'), /ACTIVE-PROFILE=prod/);
    assert.equal(run('use', 'dev').status, 0);
    fs.writeFileSync(active, '');
    assert.equal(run('status').status, 0);
    assert.notEqual(run('use', 'prod').status, 0);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
