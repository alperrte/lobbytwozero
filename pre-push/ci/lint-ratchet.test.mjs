import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  parseCheckstyle, parsePmd, evaluate, compareBaselines, parseNameStatus, ratchetCheck, updateBaseline, debtReport, readBaseline, baselineFile,
} from './lint-ratchet.mjs';

const module = 'services/demo-service';
const windowsFile = rest => `C:\\work\\repo\\backend\\services\\demo-service\\${rest.replaceAll('/', '\\')}`;
const checkstyleXml = (...errors) => `<?xml version="1.0"?>\n<checkstyle version="14.3.0">\n${errors.map(([file, rule]) =>
  `<file name="${windowsFile(file)}">\n<error line="3" column="1" severity="error" message="m" source="com.puppycrawl.tools.checkstyle.checks.${rule}Check"/>\n</file>`).join('\n')}\n<file name="${windowsFile('src/main/java/Clean.java')}">\n</file>\n</checkstyle>\n`;
const pmdXml = (...violations) => `<?xml version="1.0"?>\n<pmd version="7.28.0">\n${violations.map(([file, rule]) =>
  `<file name="${windowsFile(file)}">\n<violation beginline="9" rule="${rule}" ruleset="x">text</violation>\n</file>`).join('\n')}\n</pmd>\n`;

function project(action) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-ratchet-'));
  const write = (name, content) => { const file = path.join(root, name); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, content); return file; };
  const reports = ({ cs = [], pmd = [] }) => {
    write(`backend/${module}/target/checkstyle-result.xml`, checkstyleXml(...cs));
    write(`backend/${module}/target/pmd.xml`, pmdXml(...pmd));
  };
  const baseline = modules => write(baselineFile, JSON.stringify({ version: 1, generated: '2026-10-01', modules }));
  try { return action({ root, write, reports, baseline }); } finally { fs.rmSync(root, { recursive: true, force: true }); }
}

test('parsers count rules per module-relative file, decode entities and treat clean files as empty', () => {
  const cs = parseCheckstyle(checkstyleXml(['src/main/java/A.java', 'imports.AvoidStarImport'], ['src/main/java/A.java', 'imports.AvoidStarImport'], ['src/test/java/B.java', 'naming.MethodName']), module);
  assert.deepEqual(cs, { 'src/main/java/A.java': { 'checkstyle:AvoidStarImport': 2 }, 'src/test/java/B.java': { 'checkstyle:MethodName': 1 } });
  const pmd = parsePmd(pmdXml(['src/main/java/A.java', 'PreserveStackTrace']), module);
  assert.deepEqual(pmd, { 'src/main/java/A.java': { 'pmd:PreserveStackTrace': 1 } });
  assert.deepEqual(parsePmd('<pmd version="7"></pmd>', module), {});
  assert.throws(() => parsePmd('<pmd><error filename="x" msg="parse"/></pmd>', module), /not proven/);
  assert.throws(() => parseCheckstyle('<html/>', module), /not valid/);
  assert.throws(() => parseCheckstyle('<checkstyle><file name="/elsewhere/A.java"><error source="a.BCheck"/></file></checkstyle>', module), /outside the module/);
});

test('a violation above the frozen baseline fails, equal or lower debt passes, new clean files pass', () => {
  const baseline = { modules: { [module]: { 'src/A.java': { 'checkstyle:MethodName': 2 } } } };
  const run = current => evaluate({ baseline, current: { [module]: current } });
  assert.equal(run({ 'src/A.java': { 'checkstyle:MethodName': 2 } }).failures.length, 0);
  assert.equal(run({ 'src/A.java': { 'checkstyle:MethodName': 1 } }).failures.length, 0);
  assert.equal(run({}).failures.length, 0);
  const worse = run({ 'src/A.java': { 'checkstyle:MethodName': 3 } });
  assert.deepEqual(worse.failures.map(f => [f.type, f.baseline, f.current]), [['new', 2, 3]]);
  assert.equal(run({ 'src/A.java': { 'checkstyle:MethodName': 2, 'pmd:CloseResource': 1 } }).failures[0].rule, 'pmd:CloseResource');
  assert.equal(run({ 'src/New.java': { 'checkstyle:AvoidStarImport': 1 } }).failures[0].type, 'new');
  assert.equal(evaluate({ baseline, current: { 'services/other': { 'src/X.java': { 'pmd:A': 5 } } } }).failures[0].module, 'services/other');
});

test('an improved file that is part of the pull request must lock the gain; untouched files only get a notice', () => {
  const baseline = { modules: { [module]: { 'src/A.java': { 'pmd:CloseResource': 3 }, 'src/B.java': { 'pmd:CloseResource': 2 } } } };
  const current = { [module]: { 'src/A.java': { 'pmd:CloseResource': 1 }, 'src/B.java': { 'pmd:CloseResource': 1 } } };
  const tracked = evaluate({ baseline, current, changed: { [module]: new Set(['src/A.java']) }, trackChanges: true });
  assert.deepEqual(tracked.failures.map(f => [f.type, f.file]), [['stale', 'src/A.java']]);
  assert.equal(tracked.improvements.length, 1);
  const untracked = evaluate({ baseline, current, trackChanges: false });
  assert.equal(untracked.failures.length, 0);
  assert.equal(untracked.rows[0].fixed, 3);
});

test('a renamed file keeps its accepted debt and a deleted file must be removed from the baseline', () => {
  const baseline = { modules: { [module]: { 'src/Old.java': { 'checkstyle:MethodName': 4 } } } };
  const { changed, renames } = parseNameStatus('R100\tbackend/services/demo-service/src/Old.java\tbackend/services/demo-service/src/New.java\n');
  const moved = evaluate({ baseline, current: { [module]: { 'src/New.java': { 'checkstyle:MethodName': 4 } } }, changed, renames, trackChanges: true });
  assert.deepEqual(moved.failures.map(f => [f.type, f.file]), [['stale', 'src/Old.java']]);
  const worse = evaluate({ baseline, current: { [module]: { 'src/New.java': { 'checkstyle:MethodName': 5 } } }, changed, renames, trackChanges: true });
  assert.ok(worse.failures.some(f => f.type === 'new' && f.file === 'src/New.java'));
  const deleted = parseNameStatus('D\tbackend/services/demo-service/src/Old.java\n');
  assert.equal(evaluate({ baseline, current: { [module]: {} }, changed: deleted.changed, trackChanges: true }).failures[0].type, 'stale');
});

test('the baseline of a pull request can never claim more debt than the target branch accepted', () => {
  const base = { modules: { [module]: { 'src/A.java': { 'pmd:CloseResource': 2 } } } };
  const grow = rules => ({ modules: { [module]: { 'src/A.java': rules } } });
  assert.equal(compareBaselines(base, grow({ 'pmd:CloseResource': 2 })).length, 0);
  assert.equal(compareBaselines(base, grow({ 'pmd:CloseResource': 1 })).length, 0);
  assert.equal(compareBaselines(base, grow({ 'pmd:CloseResource': 3 }))[0].type, 'baseline-increase');
  assert.equal(compareBaselines(base, grow({ 'pmd:CloseResource': 2, 'pmd:EmptyCatchBlock': 1 }))[0].rule, 'pmd:EmptyCatchBlock');
  assert.equal(compareBaselines(base, { modules: { [module]: { 'src/Brand.java': { 'pmd:A': 1 } } } }).length, 1);
  const renames = { [module]: new Map([['src/Renamed.java', 'src/A.java']]) };
  assert.equal(compareBaselines(base, { modules: { [module]: { 'src/Renamed.java': { 'pmd:CloseResource': 2 } } } }, renames).length, 0);
});

test('name-status parsing tracks added, modified, deleted and renamed service files only', () => {
  const { changed, renames } = parseNameStatus('M\tbackend/services/a-service/src/X.java\nA\tbackend/services/b-service/Y.java\nD\tdocs/api.md\nR90\tbackend/services/a-service/o.java\tbackend/services/a-service/n.java\n');
  assert.deepEqual([...changed['services/a-service']].sort(), ['n.java', 'o.java', 'src/X.java']);
  assert.deepEqual([...changed['services/b-service']], ['Y.java']);
  assert.equal(renames['services/a-service'].get('n.java'), 'o.java');
  assert.equal(Object.keys(changed).length, 2);
});

test('the gate reads real report files, refuses missing or stale reports and writes a step summary', () => project(({ root, reports, baseline, write }) => {
  baseline({ [module]: { 'src/main/java/A.java': { 'checkstyle:AvoidStarImport': 1 } } });
  assert.throws(() => ratchetCheck({ root, modules: [module], log: () => {} }), /Missing checkstyle-result.xml/);
  reports({ cs: [['src/main/java/A.java', 'imports.AvoidStarImport']] });
  const summary = path.join(root, 'summary.md');
  const lines = [];
  const result = ratchetCheck({ root, modules: [module], log: line => lines.push(line), summaryFile: summary });
  assert.equal(result.failures.length, 0);
  assert.match(fs.readFileSync(summary, 'utf8'), /Checkstyle \/ PMD/);
  assert.match(lines.join(''), /Karşılaştırma dalı bilinmiyor/);
  assert.throws(() => ratchetCheck({ root, modules: [module], since: Date.now() + 60_000, log: () => {} }), /Stale/);
  reports({ cs: [['src/main/java/A.java', 'imports.AvoidStarImport'], ['src/main/java/B.java', 'imports.AvoidStarImport']], pmd: [['src/test/java/T.java', 'CloseResource']] });
  assert.throws(() => ratchetCheck({ root, modules: [module], log: () => {} }), error => error.category === 'QUALITY_FINDINGS' && /YENİ İHLAL/.test(error.message));
  write(baselineFile, 'not json');
  assert.throws(() => ratchetCheck({ root, modules: [module], log: () => {} }));
}));

test('update --init freezes current debt deterministically; later updates can only shrink the baseline', () => project(({ root, reports }) => {
  reports({ cs: [['src/main/java/B.java', 'imports.AvoidStarImport'], ['src/main/java/A.java', 'naming.MethodName']], pmd: [['src/main/java/A.java', 'CloseResource']] });
  const now = new Date('2026-10-27T10:00:00Z');
  const first = updateBaseline({ root, modules: [module], init: true, now });
  assert.equal(first.generated, '2026-10-27');
  assert.deepEqual(Object.keys(first.modules[module]), ['src/main/java/A.java', 'src/main/java/B.java']);
  assert.deepEqual(Object.keys(first.modules[module]['src/main/java/A.java']), ['checkstyle:MethodName', 'pmd:CloseResource']);
  assert.deepEqual(readBaseline(root), first);
  reports({ cs: [['src/main/java/B.java', 'imports.AvoidStarImport'], ['src/main/java/B.java', 'imports.AvoidStarImport']] });
  assert.throws(() => updateBaseline({ root, modules: [module], now }), /cannot grow/);
  reports({ cs: [['src/main/java/B.java', 'imports.AvoidStarImport']] });
  const shrunk = updateBaseline({ root, modules: [module], now });
  assert.deepEqual(Object.keys(shrunk.modules[module]), ['src/main/java/B.java']);
  assert.equal(ratchetCheck({ root, modules: [module], log: () => {}, summaryFile: '' }).failures.length, 0);
}));

test('the debt report lists the largest debt first, with the age of the baseline, so old debt stays visible', () => project(({ root, baseline }) => {
  baseline({ 'services/big': { 'a.java': { 'pmd:X': 3, 'checkstyle:Y': 2 } }, 'services/small': { 'b.java': { 'pmd:X': 1 } }, 'services/clean': {} });
  const lines = [];
  const rows = debtReport({ root, now: new Date('2026-10-11T00:00:00Z'), log: line => lines.push(line), summaryFile: '' });
  assert.deepEqual(rows.map(row => [row.module, row.debt]), [['services/big', 5], ['services/small', 1], ['services/clean', 0]]);
  assert.match(lines.join(''), /10 gün önce/);
  assert.match(lines.join(''), /Toplam: 6 ihlal/);
}));
