import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { repositoryRoot, readModules } from '../../lib/quality-tools.mjs';

// Checkstyle/PMD "no new violations" gate. Existing debt is frozen in backend/config/quality-baseline.json so
// it never blocks an unrelated pull request, but it can neither grow nor be hidden: the baseline itself may
// only shrink against the target branch, and improved files must lock the gain in the same pull request.
export const baselineFile = 'backend/config/quality-baseline.json';
const command = 'node pre-push/ci/lint-ratchet.mjs';

const unescape = text => text.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&amp;', '&');
const ruleOf = source => source.split('.').at(-1).replace(/Check$/, '');

function relativeToModule(name, module) {
  const normalized = unescape(name).replaceAll('\\', '/');
  const marker = `/backend/${module}/`;
  const index = normalized.indexOf(marker);
  if (index < 0) throw new Error('Quality report file is outside the module: ' + module);
  return normalized.slice(index + marker.length);
}

function add(target, file, rule) {
  const rules = (target[file] ||= {});
  rules[rule] = (rules[rule] ?? 0) + 1;
}

export function parseCheckstyle(xml, module) {
  if (!/<checkstyle[\s>]/.test(xml)) throw new Error('Checkstyle report is not valid XML');
  const result = {};
  for (const block of xml.matchAll(/<file name="([^"]*)">([\s\S]*?)<\/file>/g)) {
    for (const error of block[2].matchAll(/<error [^>]*source="([^"]*)"/g)) add(result, relativeToModule(block[1], module), 'checkstyle:' + ruleOf(error[1]));
  }
  return result;
}

export function parsePmd(xml, module) {
  if (!/<pmd[\s>]/.test(xml)) throw new Error('PMD report is not valid XML');
  // Processing errors (unparsable sources) mean the analysis is incomplete: never treat them as clean.
  if (/<error filename=/.test(xml)) throw new Error('PMD reported processing errors for ' + module + '; analysis is not proven');
  const result = {};
  for (const block of xml.matchAll(/<file name="([^"]*)">([\s\S]*?)<\/file>/g)) {
    for (const violation of block[2].matchAll(/<violation [^>]*rule="([^"]*)"/g)) add(result, relativeToModule(block[1], module), 'pmd:' + violation[1]);
  }
  return result;
}

export function collectModule(root, module, since = 0) {
  const target = path.join(root, 'backend', module, 'target');
  const merged = {};
  for (const [name, parse] of [['checkstyle-result.xml', parseCheckstyle], ['pmd.xml', parsePmd]]) {
    const file = path.join(target, name);
    if (!fs.existsSync(file)) throw new Error(`Missing ${name} for ${module}; Checkstyle/PMD did not run, so quality is not proven`);
    if (fs.statSync(file).mtimeMs < since - 2000) throw new Error(`Stale ${name} for ${module}`);
    for (const [filename, rules] of Object.entries(parse(fs.readFileSync(file, 'utf8'), module))) {
      Object.assign((merged[filename] ||= {}), rules);
    }
  }
  return merged;
}

export function emptyBaseline() {
  return { version: 1, generated: '', modules: {} };
}

export function readBaseline(root) {
  const file = path.join(root, baselineFile);
  if (!fs.existsSync(file)) throw new Error(`${baselineFile} is missing; create it once with: ${command} update --init`);
  const data = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (data?.version !== 1 || typeof data.modules !== 'object' || data.modules === null) throw new Error('Invalid quality baseline');
  for (const files of Object.values(data.modules)) {
    for (const rules of Object.values(files)) {
      for (const count of Object.values(rules)) if (!Number.isSafeInteger(count) || count < 1) throw new Error('Invalid quality baseline count');
    }
  }
  return data;
}

const total = files => Object.values(files ?? {}).reduce((sum, rules) => sum + Object.values(rules).reduce((a, b) => a + b, 0), 0);

// current: { module: { file: { rule: n } } }, changed: { module: Set(files) }, renames: { module: Map(newFile -> oldFile) }
export function evaluate({ baseline, current, changed = {}, renames = {}, trackChanges = false }) {
  const failures = [], improvements = [], rows = [];
  for (const [module, files] of Object.entries(current)) {
    const recorded = baseline.modules[module] ?? {};
    const touched = changed[module] ?? new Set();
    const moved = renames[module] ?? new Map();
    let added = 0, fixed = 0;
    for (const file of new Set([...Object.keys(files), ...Object.keys(recorded)])) {
      const now = files[file] ?? {};
      const before = recorded[file] ?? recorded[moved.get(file)] ?? {};
      for (const rule of new Set([...Object.keys(now), ...Object.keys(before)])) {
        const c = now[rule] ?? 0, b = before[rule] ?? 0;
        if (c > b) { added += c - b; failures.push({ type: 'new', module, file, rule, baseline: b, current: c }); }
        else if (c < b) {
          fixed += b - c;
          if (trackChanges && (touched.has(file) || moved.has(file))) failures.push({ type: 'stale', module, file, rule, baseline: b, current: c });
          else improvements.push({ module, file, rule, baseline: b, current: c });
        }
      }
    }
    rows.push({ module, debt: total(recorded), now: total(files), added, fixed });
  }
  return { failures, improvements, rows };
}

// The baseline in a pull request may never claim more debt than the target branch already accepted.
export function compareBaselines(base, next, renames = {}) {
  const failures = [];
  for (const [module, files] of Object.entries(next.modules)) {
    const moved = renames[module] ?? new Map();
    for (const [file, rules] of Object.entries(files)) {
      const old = base.modules[module]?.[file] ?? base.modules[module]?.[moved.get(file)] ?? {};
      for (const [rule, count] of Object.entries(rules)) {
        if (count > (old[rule] ?? 0)) failures.push({ type: 'baseline-increase', module, file, rule, baseline: old[rule] ?? 0, current: count });
      }
    }
  }
  return failures;
}

export function parseNameStatus(output) {
  const changed = {}, renames = {};
  const track = (file, rename) => {
    const match = file.match(/^backend\/(services\/[a-z][a-z0-9-]*)\/(.*)$/);
    if (!match) return null;
    (changed[match[1]] ||= new Set()).add(match[2]);
    return match;
  };
  for (const line of output.split('\n').filter(Boolean)) {
    const [status, first, second] = line.split('\t');
    if (status.startsWith('R') || status.startsWith('C')) {
      const from = first.match(/^backend\/(services\/[a-z][a-z0-9-]*)\/(.*)$/), to = track(second);
      track(first);
      if (from && to && from[1] === to[1] && status.startsWith('R')) (renames[to[1]] ||= new Map()).set(to[2], from[2]);
    } else track(first);
  }
  return { changed, renames };
}

function git(root, args) {
  return spawnSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true });
}

export function changedSince(root, base, head = 'HEAD') {
  const result = git(root, ['diff', '--name-status', '-M', '--no-ext-diff', base, head, '--', 'backend/services']);
  if (result.status !== 0) throw new Error('Cannot determine changed files for the quality gate');
  return parseNameStatus(result.stdout);
}

function baselineAt(root, base) {
  const exists = git(root, ['cat-file', '-e', `${base}:${baselineFile}`]);
  if (exists.status !== 0) return null;
  const shown = git(root, ['show', `${base}:${baselineFile}`]);
  if (shown.status !== 0) throw new Error('Cannot read the target branch quality baseline');
  const data = JSON.parse(shown.stdout);
  if (data?.version !== 1 || typeof data.modules !== 'object' || data.modules === null) throw new Error('Invalid target branch quality baseline');
  return data;
}

export function localBase(root) {
  const result = git(root, ['merge-base', 'HEAD', 'origin/main']);
  const sha = result.status === 0 ? result.stdout.trim() : '';
  return validSha(sha) ? sha : undefined;
}

const validSha = value => typeof value === 'string' && /^[a-f0-9]{40}$/.test(value) && !/^0+$/.test(value);

export function describe(failure) {
  const where = `${failure.module}/${failure.file} [${failure.rule}]`;
  if (failure.type === 'new') return `YENİ İHLAL: ${where}: izin verilen ${failure.baseline}, şimdi ${failure.current}. Bulguyu düzelt (baseline'ı büyütmek yasak).`;
  if (failure.type === 'stale') return `KAZANIMI KİLİTLE: ${where}: baseline ${failure.baseline}, şimdi ${failure.current}. Eski sayı geri dönmesin diye şunu çalıştır ve baseline dosyasını commit'e ekle: ${command} update --modules ${failure.module}`;
  return `BASELINE ARTIRILAMAZ: ${where}: hedef dalda ${failure.baseline}, bu PR'da ${failure.current}. Yeni borç baseline'a yazılarak gizlenemez.`;
}

export function summaryMarkdown(rows, failures, notes = []) {
  const lines = ['### Checkstyle / PMD: yeni ihlal denetimi', '',
    '| Servis | Eski borç (baseline) | Şimdi | Yeni | Kapanan |', '|---|---:|---:|---:|---:|'];
  for (const row of rows) lines.push(`| ${row.module} | ${row.debt} | ${row.now} | ${row.added} | ${row.fixed} |`);
  lines.push('', failures.length ? `**Sonuç: KIRMIZI** (${failures.length} sorun)` : '**Sonuç: yeşil**: yeni ihlal yok.');
  for (const failure of failures.slice(0, 40)) lines.push(`- ${describe(failure)}`);
  for (const note of notes) lines.push(`- ${note}`);
  return lines.join('\n') + '\n';
}

export function ratchetCheck({ root = repositoryRoot, modules, base, since = 0, log = console.log, summaryFile = process.env.GITHUB_STEP_SUMMARY } = {}) {
  const selected = modules ?? readModules(root);
  const baseline = readBaseline(root);
  const current = Object.fromEntries(selected.map(module => [module, collectModule(root, module, since)]));
  let changed = {}, renames = {}, extra = [];
  const tracking = validSha(base);
  if (tracking) ({ changed, renames } = changedSince(root, base));
  const result = evaluate({ baseline, current, changed, renames, trackChanges: tracking });
  const failures = [...result.failures];
  if (tracking) {
    const target = baselineAt(root, base);
    if (target) failures.push(...compareBaselines(target, baseline, renames));
    else extra.push('Hedef dalda baseline dosyası yok (ilk kurulum); artış denetimi atlandı, dosya PR incelemesinde gözden geçirilmeli.');
  } else extra.push('Karşılaştırma dalı bilinmiyor: kazanım kilitleme ve baseline artış denetimi yapılmadı (yerel çalıştırma).');
  for (const improvement of result.improvements.slice(0, 5)) extra.push(`İyileşme: ${improvement.module}/${improvement.file} [${improvement.rule}] ${improvement.baseline} → ${improvement.current}`);
  const markdown = summaryMarkdown(result.rows, failures, extra);
  log(markdown);
  if (summaryFile) { try { fs.appendFileSync(summaryFile, markdown); } catch { /* the summary is best effort */ } }
  if (failures.length) throw Object.assign(new Error(`Quality gate: ${failures.length} issue(s)\n` + failures.slice(0, 25).map(describe).join('\n')), { category: 'QUALITY_FINDINGS' });
  return result;
}

function sorted(object) {
  return Object.fromEntries(Object.entries(object).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
}

export function updateBaseline({ root = repositoryRoot, modules, init = false, since = 0, now = new Date() } = {}) {
  const selected = modules ?? readModules(root);
  const baseline = init && !fs.existsSync(path.join(root, baselineFile)) ? emptyBaseline() : readBaseline(root);
  for (const module of selected) {
    const current = collectModule(root, module, since);
    const previous = baseline.modules[module] ?? {};
    if (!init) {
      const grew = evaluate({ baseline: { modules: { [module]: previous } }, current: { [module]: current } }).failures;
      if (grew.length) throw new Error('Baseline cannot grow; fix these first:\n' + grew.slice(0, 25).map(describe).join('\n'));
    }
    baseline.modules[module] = sorted(Object.fromEntries(Object.entries(current).filter(([, rules]) => Object.keys(rules).length).map(([file, rules]) => [file, sorted(rules)])));
  }
  baseline.generated = now.toISOString().slice(0, 10);
  baseline.modules = sorted(baseline.modules);
  fs.mkdirSync(path.dirname(path.join(root, baselineFile)), { recursive: true });
  fs.writeFileSync(path.join(root, baselineFile), JSON.stringify(baseline, null, 1) + '\n');
  return baseline;
}

export function debtReport({ root = repositoryRoot, now = new Date(), summaryFile = process.env.GITHUB_STEP_SUMMARY, log = console.log } = {}) {
  const baseline = readBaseline(root);
  const age = baseline.generated ? Math.floor((now - new Date(baseline.generated)) / 86_400_000) : null;
  const rows = Object.entries(baseline.modules).map(([module, files]) => {
    const byRule = {};
    for (const rules of Object.values(files)) for (const [rule, count] of Object.entries(rules)) byRule[rule] = (byRule[rule] ?? 0) + count;
    return { module, files: Object.keys(files).length, debt: total(files), byRule };
  }).sort((a, b) => b.debt - a.debt);
  const lines = ['### Kalite borcu (Checkstyle / PMD): eski ihlaller', '',
    `Baseline tarihi: ${baseline.generated || '?'}${age === null ? '' : ` (${age} gün önce)`}. Borç yalnızca azalabilir; yeni ihlal PR'ı kırmızı yapar.`, '',
    '| Servis | Borçlu dosya | Toplam ihlal | Kural dağılımı |', '|---|---:|---:|---|'];
  for (const row of rows) lines.push(`| ${row.module} | ${row.files} | ${row.debt} | ${Object.entries(row.byRule).map(([rule, count]) => `${rule}: ${count}`).join(', ') || '-'} |`);
  lines.push('', `**Toplam: ${rows.reduce((sum, row) => sum + row.debt, 0)} ihlal.** Borcunu kapatan ekip: \`${command} update --modules services/<servis>\` ile kazancı kilitler.`);
  const markdown = lines.join('\n') + '\n';
  log(markdown);
  if (summaryFile) { try { fs.appendFileSync(summaryFile, markdown); } catch { /* best effort */ } }
  return rows;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [action, ...args] = process.argv.slice(2);
    const option = name => { const index = args.indexOf(name); return index >= 0 ? args[index + 1] : undefined; };
    const modules = option('--modules')?.split(',');
    if (action === 'check') {
      ratchetCheck({ modules, base: option('--base') ?? process.env.LTZ_QUALITY_BASE_SHA ?? process.env.COVERAGE_BASE_SHA, since: Number(option('--since') ?? 0) });
    } else if (action === 'update') {
      updateBaseline({ modules, init: args.includes('--init') });
      console.log('Baseline updated: ' + baselineFile);
      const { writeDocs } = await import('./security-docs.mjs');
      console.log('docs-security refreshed (' + writeDocs().length + ' files)');
    } else if (action === 'docs') {
      const { writeDocs } = await import('./security-docs.mjs');
      console.log('docs-security refreshed (' + writeDocs().length + ' files)');
    } else if (action === 'report') debtReport();
    else throw new Error(`Use: ${command} check|update|docs|report [--modules services/a,services/b] [--base <sha>] [--init]`);
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
