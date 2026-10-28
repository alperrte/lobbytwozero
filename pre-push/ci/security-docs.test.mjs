import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { generateDocs, writeDocs, renderNotes, docsDirectory } from './security-docs.mjs';
import { repositoryRoot, readModules } from '../../lib/quality-tools.mjs';

const baseline = {
  version: 1, generated: '2026-10-28',
  modules: {
    'services/auth-service': {},
    'services/game-service': {
      'src/main/java/com/ltz/game/A.java': { 'checkstyle:AvoidStarImport': 2, 'pmd:CloseResource': 1 },
      'src/test/java/com/ltz/game/B.java': { 'checkstyle:AvoidStarImport': 1 },
    },
  },
};
const modules = ['services/auth-service', 'services/game-service', 'services/api-gateway'];

test('every service gets a generated README with owner, frozen debt per rule and per file, and the agent rules', () => {
  const files = generateDocs({ baseline, modules });
  assert.deepEqual([...files.keys()].sort(), [`${docsDirectory}/README.md`, `${docsDirectory}/api-gateway/README.md`, `${docsDirectory}/auth-service/README.md`, `${docsDirectory}/game-service/README.md`]);
  const game = files.get(`${docsDirectory}/game-service/README.md`);
  assert.match(game, /\*\*Sahip:\*\* Hamza \(@HmzT270\)/);
  assert.match(game, /⚠️ 4 ihlal, 2 dosya/);
  assert.match(game, /`checkstyle:AvoidStarImport` \| 3 \|/);
  assert.match(game, /`pmd:CloseResource` \| 1 \|/);
  assert.match(game, /`src\/main\/java\/com\/ltz\/game\/A\.java`: checkstyle:AvoidStarImport ×2, pmd:CloseResource ×1/);
  assert.match(game, /gizleme|gizleme\*\*/);
  assert.match(game, /lint-ratchet\.mjs check --modules services\/game-service/);
  assert.match(game, /-Pstrict-quality/);
  assert.match(files.get(`${docsDirectory}/auth-service/README.md`), /✅ yok \(temiz\)/);
  assert.match(files.get(`${docsDirectory}/api-gateway/README.md`), /Ortak \(sahip atanmadı\)/);
  const index = files.get(`${docsDirectory}/README.md`);
  assert.match(index, /\| game-service \| Hamza \| 4 \|/);
  assert.match(index, /\| auth-service \| Kerem \| ✅ 0 \|/);
  assert.match(index, /Toplam donmuş borç: 4/);
});

test('generation is deterministic and never touches the hand-written notes', () => {
  assert.deepEqual([...generateDocs({ baseline, modules })], [...generateDocs({ baseline, modules })]);
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-secdocs-'));
  try {
    writeDocs({ root, baseline, modules });
    const notes = path.join(root, docsDirectory, 'game-service', 'NOTLAR.md');
    assert.equal(fs.readFileSync(notes, 'utf8'), renderNotes('game-service'));
    fs.writeFileSync(notes, 'ekibin kendi notu\n');
    fs.writeFileSync(path.join(root, docsDirectory, 'game-service', 'README.md'), 'elle bozuldu');
    writeDocs({ root, baseline, modules });
    assert.equal(fs.readFileSync(notes, 'utf8'), 'ekibin kendi notu\n');
    assert.match(fs.readFileSync(path.join(root, docsDirectory, 'game-service', 'README.md'), 'utf8'), /güvenlik ve kalite durumu/);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test('the committed docs-security folder matches the committed baseline for every backend module', () => {
  const normalize = text => text.replace(/\r\n/g, '\n');
  for (const [relative, expected] of generateDocs({ root: repositoryRoot })) {
    const file = path.join(repositoryRoot, relative);
    assert.ok(fs.existsSync(file), `${relative} is missing; run: node pre-push/ci/lint-ratchet.mjs docs`);
    assert.equal(normalize(fs.readFileSync(file, 'utf8')), expected, `${relative} is stale; run: node pre-push/ci/lint-ratchet.mjs docs`);
  }
  for (const module of readModules(repositoryRoot)) {
    const service = module.replace(/^services\//, '');
    assert.ok(fs.existsSync(path.join(repositoryRoot, docsDirectory, service, 'NOTLAR.md')), `${service}/NOTLAR.md is missing`);
  }
});
