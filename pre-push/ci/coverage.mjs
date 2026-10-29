import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { readModules, repositoryRoot, generatedPath } from '../../lib/quality-tools.mjs';

export const policyPath = 'backend/config/coverage-policy.json';
export function coverageMinimum(root = repositoryRoot) {
  const value = JSON.parse(fs.readFileSync(path.join(root, policyPath), 'utf8')).minimumPercent;
  if (!Number.isSafeInteger(value) || value < 80 || value > 100) throw new Error('Invalid coverage policy minimum');
  return value;
}
export function mavenBuildStart(timestamp, skipFlags) {
  const started = Date.parse(timestamp);
  if (!Number.isSafeInteger(started) || started <= 0 || started > Date.now() + 2000
      || skipFlags.length !== 4 || skipFlags.some(value => value !== 'false')) throw new Error('Coverage: invalid Maven start or test/JaCoCo skip flag; verify cannot skip measurement');
  return started;
}
const sha = text => createHash('sha256').update(text).digest('hex');
const fail = message => { throw new Error('Coverage: ' + message); };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
function plain(file) {
  for (let p = path.resolve(file); ; p = path.dirname(p)) {
    if (fs.lstatSync(p).isSymbolicLink()) fail('symbolic link refused');
    if (path.dirname(p) === p) break;
  }
  return file;
}
function files(dir, extension) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(plain(dir), { withFileTypes: true }).flatMap(entry => {
    const file = path.join(dir, entry.name);
    if (entry.isSymbolicLink()) fail('source/report link refused');
    return entry.isDirectory() ? files(file, extension) : entry.isFile() && file.endsWith(extension) ? [file] : [];
  }).sort();
}
// Java lexical tokens, not a Java compiler. Comments/spacing alone are ignored;
// uncertain non-comment changes without line metadata fail closed below.
export function javaTokens(text) {
  // javac expands Unicode escapes before it recognizes comments. Refuse that
  // ambiguous spelling rather than treating encoded executable code as a comment.
  if (/\\u+[a-fA-F0-9]{4}/.test(text)) fail('Unicode escapes require compiler-aware review; cannot grant a comment/bootstrap waiver');
  return text.replace(/\/\*[\s\S]*?\*\/|\/\/[^\r\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g,
    token => token.startsWith('//') || token.startsWith('/*') ? ' ' : token)
    .match(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|\d+(?:[.\w]*)|[^\s]/g) ?? [];
}
// An edit that only adds, removes or reorders import declarations changes no behavior: javac and the module tests
// still verify it. Any other token, including a package or class change in the same file, keeps the file semantic.
export function withoutImports(tokens) {
  const kept = [];
  for (let i = 0; i < tokens.length; i++) {
    if (tokens[i] !== 'import') { kept.push(tokens[i]); continue; }
    while (i < tokens.length && tokens[i] !== ';') i++;
  }
  return kept;
}
function bootstrapExemptions(text, filename, measured) {
  const clean = text.replace(/\/\*[\s\S]*?\*\/|\/\/[^\r\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g,
    token => token.startsWith('//') || token.startsWith('/*') ? token.replace(/[^\r\n]/g, ' ') : token);
  const tokens = [], rows = [];
  let previous = 0, row = 1;
  for (const match of clean.matchAll(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|\d+(?:[.\w]*)|[^\s]/g)) {
    row += (clean.slice(previous, match.index).match(/\n/g) ?? []).length;
    tokens.push(match[0]); rows.push(row); previous = match.index;
  }
  const className = path.basename(filename, '.java');
  const find = sequence => {
    const matches = [];
    for (let i = 0; i <= tokens.length - sequence.length; i++) if (sequence.every((token, j) => tokens[i+j] === token)) matches.push(i);
    return matches.length === 1 ? matches[0] : -1;
  };
  const result = new Set(), cls = find(['public','class',className,'{']);
  if (cls >= 0 && measured.has(rows[cls])) result.add(rows[cls]);
  // Only the exact, side-effect-free bootstrap method can retain baseline debt.
  // Any extra token in main removes its exemption; new methods never inherit it.
  const main = javaTokens(`public static void main(String[] args) { SpringApplication.run(${className}.class, args); }`);
  const start = find(main);
  if (start >= 0) for (const nr of measured.keys()) if (nr >= rows[start] && nr <= rows[start+main.length-1]) result.add(nr);
  return result;
}
export function sourceInventory(root, module, raw = false) {
  const dir = path.join(root, 'backend', module, 'src/main/java');
  return Object.fromEntries(files(dir, '.java').map(file => {
    const content = fs.readFileSync(file);
    return [path.relative(dir, file).split(path.sep).join('/'), sha(raw ? content : JSON.stringify(javaTokens(content.toString('utf8'))))];
  }));
}
export function validatePolicy(policy, modules, previous = null) {
  if (policy.version !== 1 || !Number.isInteger(policy.minimumPercent) || policy.minimumPercent < 80 || policy.minimumPercent > 100
      || !policy.modules || !same(Object.keys(policy.modules).sort(), [...modules].sort())) fail('policy/modules/threshold invalid; new modules require explicit strict registration');
  for (const [module, item] of Object.entries(policy.modules)) {
    const b = item.baseline;
    if (!['bootstrap', 'strict'].includes(item.level) || !b || !Number.isSafeInteger(b.covered) || !Number.isSafeInteger(b.missed)
        || b.covered < 0 || b.missed < 0 || b.covered + b.missed === 0 || !Number.isSafeInteger(b.tests) || b.tests < 1
        || !item.sources || !Object.keys(item.sources).length
        || Object.entries(item.sources).some(([name, hash]) => !/^(?:[A-Za-z_$][\w$]*\/)*[A-Za-z_$][\w$]*\.java$/.test(name) || !/^[a-f0-9]{64}$/.test(hash))) fail('invalid module baseline: ' + module);
    const old = previous?.modules?.[module];
    if (old) {
      if (old.level === 'strict' && item.level !== 'strict') fail('strict downgrade refused');
      if (!same(old.baseline, b) || !same(old.sources, item.sources)) fail('bootstrap debt baseline is immutable; cannot rewrite denominator/exemptions');
    } else if (previous && item.level !== 'strict') fail('new modules cannot acquire bootstrap privilege');
  }
  if (previous && (policy.minimumPercent < previous.minimumPercent
      || Object.keys(previous.modules).some(module => !policy.modules[module]))) fail('policy weakening/module removal requires a separate policy design; refused');
  return !previous || !same(policy, previous);
}
export function gitContext(root, { base = process.env.COVERAGE_BASE_SHA || 'origin/main', head = process.env.COVERAGE_HEAD_SHA,
  prHead = process.env.COVERAGE_PR_HEAD_SHA, run = spawnSync } = {}) {
  const git = args => {
    const env = Object.fromEntries(['PATH','Path','SystemRoot','WINDIR','ComSpec','TEMP','TMP'].filter(key => process.env[key]).map(key => [key,process.env[key]]));
    Object.assign(env, { GIT_CONFIG_GLOBAL: process.platform === 'win32' ? 'NUL' : '/dev/null', GIT_CONFIG_NOSYSTEM: '1', GIT_OPTIONAL_LOCKS: '0', GIT_TERMINAL_PROMPT: '0' });
    const result = run('git', ['-c','safe.directory=' + path.resolve(root),'-c','core.fsmonitor=false', ...args], { cwd: root, env, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, windowsHide: true });
    if (result.status !== 0 || result.error) fail('Git diff/merge-base unavailable; no fallback');
    return result.stdout;
  };
  const resolve = ref => {
    if (!/^[A-Za-z0-9_./-]+$/.test(ref) || ref.startsWith('-') || ref.includes('..')) fail('invalid Git ref');
    const value = git(['rev-parse', '--verify', ref + '^{commit}']).trim();
    if (!/^[a-f0-9]{40}$/.test(value)) fail('invalid commit');
    return value;
  };
  const baseSha = resolve(base), checkedHead = resolve('HEAD'), headSha = head ? resolve(head) : checkedHead;
  if (headSha !== checkedHead) fail('coverage must describe checked-out HEAD');
  const mergeBase = git(['merge-base', baseSha, prHead ? resolve(prHead) : headSha]).trim();
  if (!/^[a-f0-9]{40}$/.test(mergeBase)) fail('missing merge-base');
  // CI compiles the merge commit: compare the current base tip to that merge,
  // not PR-head line numbers against different compiled merge sources.
  if (prHead) {
    git(['merge-base', '--is-ancestor', baseSha, headSha]);
    git(['merge-base', '--is-ancestor', resolve(prHead), headSha]);
  }
  const comparison = head ? baseSha : mergeBase;
  const changed = git(['diff', '--no-ext-diff', '--no-textconv', '--no-renames', '--name-only', '-z', comparison, ...(head ? [headSha] : []), '--', 'backend/']);
  const names = new Set(changed.split('\0').filter(Boolean));
  if (!head) for (const name of git(['ls-files', '--others', '--exclude-standard', '-z', '--', 'backend/']).split('\0').filter(Boolean)) names.add(name);
  const changes = {};
  // Modules whose test sources changed: a non-executable (interface/constant) change is only accepted together with tests.
  const testModules = new Set();
  for (const name of names) {
    if (!/^backend\/services\/[a-z][a-z0-9-]*\/src\/main\/java\/(?:[A-Za-z_$][\w$]*\/)*[A-Za-z_$][\w$]*\.java$/.test(name)) {
      // Test sources are never measured for coverage, so editing a test must not fail the build; any other Java
      // file outside src/main/java stays unsupported and fails closed.
      const testSource = /^backend\/(services\/[a-z][a-z0-9-]*)\/src\/test\/java\//.exec(name);
      if (name.endsWith('.java') && !testSource) fail('unsupported Java source path');
      if (testSource && name.endsWith('.java')) testModules.add(testSource[1]);
      continue;
    }
    const existsBefore = git(['ls-tree', '--name-only', comparison, '--', name]).trim() !== '';
    const old = existsBefore ? git(['show', comparison + ':' + name]) : '';
    const absolute = path.join(root, name);
    const now = fs.existsSync(absolute) ? fs.readFileSync(plain(absolute), 'utf8') : '';
    if (head) {
      const atHead = git(['ls-tree', '--name-only', headSha, '--', name]).trim() !== '';
      if (atHead !== fs.existsSync(absolute) || (atHead && now.replace(/\r\n/g,'\n') !== git(['show', headSha + ':' + name]).replace(/\r\n/g,'\n'))) fail('checked-out source differs from compiled Git head');
    }
    // No rename heuristic: rename = verified deletion + added file, safely remeasured.
    const patch = git(['diff', '--no-ext-diff', '--no-textconv', '--no-renames', '--unified=0', comparison, ...(head ? [headSha] : []), '--', name]);
    const lines = new Set();
    if (!existsBefore) now.split(/\r?\n/).forEach((_, i) => lines.add(i + 1));
    else for (const match of patch.matchAll(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/gm)) {
      const start = Number(match[1]), length = match[2] === undefined ? 1 : Number(match[2]);
      for (let i = start; i < start + length; i++) lines.add(i);
    }
    changes[name] = { lines: [...lines], semantic: !same(withoutImports(javaTokens(old)), withoutImports(javaTokens(now))), deleted: now === '', sourceHash: sha(now) };
  }
  const previous = git(['ls-tree', '--name-only', comparison, '--', policyPath]).trim()
    ? JSON.parse(git(['show', comparison + ':' + policyPath])) : null;
  return { version: 1, base: comparison, head: headSha, mergeBase, changes, testModules: [...testModules].sort(), previous };
}
export async function ownerApprovedPolicy({ repository, pullNumber, head, author, owner, token, fetcher = fetch }) {
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository ?? '') || !Number.isSafeInteger(pullNumber) || pullNumber < 1
      || !/^[a-f0-9]{40}$/.test(head ?? '') || repository.split('/')[0].toLowerCase() !== owner?.toLowerCase() || author?.toLowerCase() === owner?.toLowerCase()) return false;
  let latest = null;
  // Bound pagination; incomplete data is never an approval.
  for (let page = 1; page <= 20; page++) {
    const response = await fetcher(`https://api.github.com/repos/${repository}/pulls/${pullNumber}/reviews?per_page=100&page=${page}`, {
      headers: { Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      signal: AbortSignal.timeout(15000), redirect: 'error',
    });
    if (!response.ok) return false;
    const rows = await response.json();
    if (!Array.isArray(rows)) return false;
    for (const row of rows) if (row.user?.login?.toLowerCase() === owner.toLowerCase()
        && ['APPROVED', 'CHANGES_REQUESTED', 'DISMISSED'].includes(row.state)) latest = row;
    if (rows.length < 100) return latest?.state === 'APPROVED' && latest.commit_id === head;
  }
  return false;
}
export async function prepareCoverage(root, { context = gitContext(root), env = process.env, started = Date.now() } = {}) {
  if (!Number.isSafeInteger(started) || started <= 0 || started > Date.now() + 2000) fail('invalid build start timestamp');
  const policy = JSON.parse(fs.readFileSync(plain(path.join(root, policyPath)), 'utf8'));
  const reviewRequired = validatePolicy(policy, readModules(root), context.previous);
  let approved = !reviewRequired;
  if (reviewRequired && env.GITHUB_ACTIONS === 'true' && env.GITHUB_EVENT_PATH) {
    const event = JSON.parse(fs.readFileSync(env.GITHUB_EVENT_PATH, 'utf8'));
    let request = event.pull_request;
    if (!request && env.GITHUB_EVENT_NAME === 'push' && env.GITHUB_REF === 'refs/heads/main'
        && /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(env.GITHUB_REPOSITORY ?? '') && /^[a-f0-9]{40}$/.test(event.after ?? '')) {
      const response = await fetch(`https://api.github.com/repos/${env.GITHUB_REPOSITORY}/commits/${event.after}/pulls`, {
        headers: { Accept:'application/vnd.github+json', ...(env.GITHUB_TOKEN ? {Authorization:'Bearer '+env.GITHUB_TOKEN} : {}) },
        signal:AbortSignal.timeout(15000),redirect:'error',
      });
      if (response.ok) {
        const rows = await response.json();
        if (Array.isArray(rows)) request = rows.find(pr => pr.merged_at && pr.merge_commit_sha === event.after && pr.base?.repo?.full_name === env.GITHUB_REPOSITORY);
      }
    }
    approved = await ownerApprovedPolicy({ repository: env.GITHUB_REPOSITORY, owner: env.GITHUB_REPOSITORY_OWNER,
      pullNumber: request?.number, head: request?.head?.sha,
      author: request?.user?.login, token: env.GITHUB_TOKEN });
  }
  if (!approved) fail('POLICY_REVIEW_REQUIRED: current-head independent repository-owner approval is required for initial policy/promotion; no local bypass');
  const result = { ...context, started, policyHash: sha(JSON.stringify(policy)), inventory: {}, approved };
  for (const module of readModules(root)) result.inventory[module] = sourceInventory(root, module, true);
  const destination = generatedPath(root, '.cache/coverage-context.json');
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.writeFileSync(destination, JSON.stringify(result));
  return destination;
}
let helperDirectory;
export function xmlMetadata(root, module, started) {
  const base = path.join(root, 'backend', module, 'target');
  const report = plain(path.join(base, 'site/jacoco/jacoco.xml'));
  const tests = plain(path.join(base, 'surefire-reports'));
  for (const file of [report, ...files(tests, '.xml').filter(f => path.basename(f).startsWith('TEST-'))]) {
    if (fs.statSync(file).mtimeMs < started - 2000) fail('stale coverage/test report');
  }
  const helper = fileURLToPath(new URL('./CoverageXml.java', import.meta.url));
  const bin = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
  if (!helperDirectory) {
    helperDirectory = generatedPath(repositoryRoot, '.cache/coverage-reader/' + sha(fs.readFileSync(helper)).slice(0, 16));
    fs.mkdirSync(helperDirectory, { recursive: true });
    const compilation = spawnSync(bin('javac'), ['-encoding', 'UTF-8', '-d', helperDirectory, helper], { encoding: 'utf8', timeout: 60000, windowsHide: true });
    if (compilation.status !== 0 || compilation.error) { helperDirectory = null; fail('XML reader compilation unavailable; raw output withheld'); }
  }
  const result = spawnSync(bin('java'), ['-cp', helperDirectory, 'CoverageXml', report, tests], { encoding: 'utf8', timeout: 30000, windowsHide: true, maxBuffer: 32 * 1024 * 1024 });
  if (result.status !== 0 || result.error) fail('XML/test parser failed; raw output withheld');
  return JSON.parse(result.stdout);
}
export function evaluateModule({ root, module, policy, context, metadata }) {
  const item = policy.modules[module];
  if (!item) fail('unregistered module');
  if (![metadata.tests, metadata.skipped, metadata.failures, metadata.errors].every(n => Number.isSafeInteger(n) && n >= 0)
      || !metadata.tests || metadata.skipped || metadata.failures || metadata.errors) fail('zero/failed/skipped tests: ' + module);
  // Existing POMs have <name/> and old reports therefore have an empty title.
  // Sourcefile paths and the complete bytecode inventory remain mandatory identity.
  if (metadata.name && metadata.name !== module.split('/')[1]) fail('wrong service/report mapping');
  const inventory = sourceInventory(root, module);
  if (context.inventory && !same(sourceInventory(root,module,true), context.inventory[module])) fail('sources changed after context preparation');
  const total = metadata.counter.covered + metadata.counter.missed;
  if (![metadata.counter.covered, metadata.counter.missed, total].every(n => Number.isSafeInteger(n) && n >= 0) || !total) fail('invalid/zero counters');
  const mapped = new Map();
  let sumCovered = 0, sumMissed = 0;
  for (const source of metadata.sources) {
    if (!Object.hasOwn(inventory, source.path) || mapped.has(source.path)) fail('sourcefile/module mapping mismatch');
    const length = fs.readFileSync(path.join(root, 'backend', module, 'src/main/java', source.path), 'utf8').split(/\r?\n/).length;
    const lines = new Map();
    let covered = 0, missed = 0;
    for (const line of source.lines) {
      if (![line.nr, line.mi, line.ci, line.mb, line.cb].every(n => Number.isSafeInteger(n) && n >= 0)
          || line.nr < 1 || line.nr > length || lines.has(line.nr)) fail('invalid/duplicate line metadata');
      lines.set(line.nr, line);
      if (line.ci > 0) covered++; else if (line.mi > 0) missed++;
    }
    if (covered !== source.counter.covered || missed !== source.counter.missed) fail('source counters inconsistent');
    sumCovered += covered; sumMissed += missed; mapped.set(source.path, lines);
  }
  if (sumCovered !== metadata.counter.covered || sumMissed !== metadata.counter.missed) fail('report counters inconsistent');
  if (Object.keys(inventory).some(source => !mapped.has(source))) fail('production Java source absent from report');
  const reportedClasses = metadata.classes.map(c => c.name + '.class').sort();
  const classesDir = path.join(root, 'backend', module, 'target/classes');
  const actualClasses = files(classesDir, '.class').map(f => path.relative(classesDir, f).split(path.sep).join('/')).sort();
  if (!actualClasses.length || !same(reportedClasses, actualClasses)) fail('compiled Java class absent from JaCoCo report');
  for (const c of metadata.classes) {
    const source = c.name.slice(0, c.name.lastIndexOf('/') + 1) + c.source;
    if (!mapped.has(source)) fail('class/sourcefile metadata missing');
  }
  const b = item.baseline, baselineTotal = b.covered + b.missed, min = policy.minimumPercent;
  if (total < baselineTotal || metadata.counter.covered < b.covered
      || metadata.counter.covered * baselineTotal < b.covered * total) fail('baseline regression/denominator reduction: ' + module);
  const overall = metadata.counter.covered * 100 / total;
  if (item.level === 'strict' && overall < min) fail('strict overall coverage below policy');
  // Fixed bootstrap debt cannot grow: all additional executable lines must retain
  // the policy threshold on every run, including PRs that remove tests only.
  let additionalCovered = 0, additionalTotal = 0;
  if (item.level === 'bootstrap') {
    for (const [sourceName, lines] of mapped) {
      const text = fs.readFileSync(path.join(root, 'backend', module, 'src/main/java', sourceName), 'utf8');
      const exempt = Object.hasOwn(item.sources, sourceName) ? bootstrapExemptions(text, sourceName, lines) : new Set();
      // Unchanged initial source is a measured baseline, not an open exemption
      // for new files. Its line cardinality must remain the verified baseline.
      const unchanged = same(inventory, item.sources) && total === baselineTotal;
      for (const [nr, line] of lines) if (line.mi + line.ci > 0 && !unchanged && !exempt.has(nr)) {
        additionalTotal++; if (line.ci > 0) additionalCovered++;
      }
    }
    if (additionalTotal && additionalCovered * 100 < min * additionalTotal) fail('cumulative additional-code coverage below policy');
  }
  if (item.level === 'bootstrap' && total === baselineTotal && !same(inventory, item.sources) && overall < min) fail('changed logic cannot hide inside bootstrap line budget');
  let changedCovered = 0, changedTotal = 0, semanticFiles = 0;
  const reasons = [], exempt = [];
  const prefix ='backend/' + module + '/src/main/java/';
  for (const [name, change] of Object.entries(context.changes)) {
    if (!/^backend\/services\/[a-z][a-z0-9-]*\/src\/main\/java\/(?:[A-Za-z_$][\w$]*\/)*[A-Za-z_$][\w$]*\.java$/.test(name)
        || typeof change.semantic !== 'boolean' || typeof change.deleted !== 'boolean' || !/^[a-f0-9]{64}$/.test(change.sourceHash)
        || !Array.isArray(change.lines) || change.lines.some(n => !Number.isSafeInteger(n) || n < 1)) fail('invalid diff context');
    if (!name.startsWith(prefix)) continue;
    if (change.deleted) {
      if (fs.existsSync(path.join(root, name))) fail('deleted source still exists');
      reasons.push('deleted source; no head executable lines'); continue;
    }
    const sourceName = name.slice(prefix.length), file = path.join(root, name);
    if (sha(fs.readFileSync(plain(file))) !== change.sourceHash) fail('diff/source hash mismatch');
    if (!change.semantic) { reasons.push('comments/whitespace only'); continue; }
    semanticFiles++;
    const lines = mapped.get(sourceName);
    if (!lines) fail('changed Java source has no measured sourcefile');
    let executable = 0;
    for (const nr of change.lines) {
      const line = lines.get(nr);
      if (line && line.mi + line.ci > 0) { executable++; changedTotal++; if (line.ci > 0) changedCovered++; }
    }
    if (!executable) {
      // Narrow exception: JaCoCo saw this compiled source and reports no executable line anywhere in it (interface
      // declarations, constants). A file with any executable line, such as a default/static body, never qualifies and
      // keeps the normal changed-line rule. The change must also come with changed tests in the same module.
      const measuredClass = metadata.classes.some(c => c.name.slice(0, c.name.lastIndexOf('/') + 1) + c.source === sourceName);
      const noExecutableCode = [...lines.values()].every(line => line.mi + line.ci === 0);
      if (!measuredClass || !noExecutableCode) fail('semantic Java change without measurable changed executable lines; review required');
      if (!Array.isArray(context.testModules) || !context.testModules.includes(module)) {
        fail('non-executable Java change (interface/constants) needs changed tests in the same module; review required');
      }
      exempt.push({ file: sourceName, reason: 'no executable lines in the whole compiled source (interface/constants); tests changed in module' });
    }
  }
  if (changedTotal && changedCovered * 100 < min * changedTotal) fail('changed-line coverage below policy');
  return { module, level: item.level, tests: metadata.tests, covered: metadata.counter.covered, missed: metadata.counter.missed,
    overallPercent: overall, baseline: 'PASS', additional: { covered: additionalCovered, total: additionalTotal }, changed: { status: changedTotal ? 'PASS' : 'NOT_APPLICABLE', covered: changedCovered,
      total: changedTotal, percent: changedTotal ? changedCovered * 100 / changedTotal : null,
      reason: changedTotal ? 'JaCoCo sourcefile/line mapped to Git additions' : reasons.join('; ') || 'no changed production Java sources', semanticFiles },
    exempt };
}
export function enforceCoverage(root, modules, contextFile = process.env.LTZ_COVERAGE_CONTEXT) {
  if (!contextFile) fail('coverage context required');
  if (path.basename(contextFile) !== 'coverage-context.json' || path.basename(path.dirname(contextFile)) !== '.cache') fail('unexpected coverage context location');
  const witness = path.dirname(path.dirname(path.resolve(contextFile)));
  const context = JSON.parse(fs.readFileSync(plain(contextFile), 'utf8'));
  const policy = JSON.parse(fs.readFileSync(plain(path.join(root, policyPath)), 'utf8'));
  // Context is not an authority to choose a convenient base or hide a diff.
  // Re-read read-only Git facts from the original checkout even for a filtered copy.
  const facts = gitContext(witness);
  if (['base','head','mergeBase','changes','testModules','previous'].some(key => !same(context[key], facts[key]))) fail('Git facts changed or context forged');
  const reviewRequired = validatePolicy(policy, readModules(root), facts.previous);
  if (reviewRequired && process.env.GITHUB_ACTIONS !== 'true') fail('POLICY_REVIEW_REQUIRED; a local context cannot assert owner approval');
  if (sha(fs.readFileSync(plain(path.join(witness,policyPath)))) !== sha(fs.readFileSync(plain(path.join(root,policyPath))))) fail('copy/witness policy mismatch');
  if (modules.some(module => !same(sourceInventory(witness,module,true), sourceInventory(root,module,true)))) fail('copy/witness source mismatch');
  if (context.approved !== true || context.policyHash !== sha(JSON.stringify(policy)) || !Number.isSafeInteger(context.started)) fail('unreviewed/stale policy context');
  return modules.map(module => evaluateModule({ root, module, policy, context, metadata: xmlMetadata(root, module, context.started) }));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv[2] === 'prepare') {
      const file = await prepareCoverage(repositoryRoot);
      if (process.env.GITHUB_ENV) {
        const entries = ['LTZ_COVERAGE_CONTEXT=' + file];
        for (const key of ['COVERAGE_BASE_SHA','COVERAGE_HEAD_SHA','COVERAGE_PR_HEAD_SHA']) {
          const value = process.env[key];
          if (value) { if (!/^[a-f0-9]{40}$/.test(value)) fail('invalid CI coverage SHA'); entries.push(key+'='+value); }
        }
        fs.appendFileSync(process.env.GITHUB_ENV,entries.join('\n')+'\n');
      }
      console.log(file);
    } else if (process.argv[2] === 'maven-module') {
      const moduleDirectory = path.resolve(process.argv[3]);
      // Running verify directly must use the Maven session start, not the time
      // after tests finished. package/image builds do not invoke this gate.
      const started = mavenBuildStart(process.argv[4],process.argv.slice(5));
      const contextFile = process.env.LTZ_COVERAGE_CONTEXT || await prepareCoverage(repositoryRoot, { started });
      if (moduleDirectory === path.join(repositoryRoot, 'backend')) {
        // Aggregator has no classes/tests; children each run the real gate.
        console.log('Coverage aggregator: per-module gate follows');
      } else {
        const module = path.relative(path.join(repositoryRoot, 'backend'), moduleDirectory).split(path.sep).join('/');
        if (!readModules(repositoryRoot).includes(module)) fail('unknown Maven module');
        const result = enforceCoverage(repositoryRoot, [module], contextFile)[0];
        for (const item of result.exempt) console.log(`COVERAGE-EXEMPT ${module}/${item.file}: ${item.reason}`);
        console.log(JSON.stringify(result));
      }
    } else throw new Error('Use prepare or maven-module <module-directory>');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
