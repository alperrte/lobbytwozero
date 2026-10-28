import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { repositoryRoot } from '../../lib/quality-tools.mjs';
import { sensitiveFile } from '../runtime-checks.mjs';
import { toolPath } from './install-tool.mjs';

function scannerContract(report, args, { expectedTargets = [], expectedArtifacts = [] }) {
  const fail = () => { throw Object.assign(new Error('Trivy report identity/schema/target coverage unverified'),
    { category: 'SCANNER_ERROR', stage: 'target-coverage' }); };
  const mode = args[0], artifact = args.at(-1), image = mode === 'image';
  const normalized = value => typeof value === 'string' ? value.replaceAll('\\', '/').replace(/\/$/, '') : null;
  if (!['image', 'config', 'fs', 'rootfs'].includes(mode) || report?.SchemaVersion !== 2
      || report.ArtifactType !== (image ? 'container_image' : 'filesystem')
      || normalized(report.ArtifactName) !== normalized(artifact)
      || !Array.isArray(report.Results) || !report.Results.length) fail();
  const seen = new Set(), covered = new Set();
  let os = false;
  for (const target of report.Results) {
    if (!target || typeof target.Target !== 'string' || !target.Target || /[\x00-\x1f]/.test(target.Target)
        || typeof target.Type !== 'string' || !/^[a-z0-9-]+$/.test(target.Type)
        || !['os-pkgs', 'lang-pkgs', 'config'].includes(target.Class)) fail();
    const key = target.Class + ':' + target.Target;
    if (seen.has(key)) fail();
    seen.add(key);
    for (const field of ['Vulnerabilities', 'Misconfigurations', 'Packages']) {
      if (target[field] !== undefined && !Array.isArray(target[field])) fail();
    }
    if (image && target.Class === 'os-pkgs') {
      if (os || !(target.Target === artifact || target.Target.startsWith(artifact + ' ('))) fail();
      os = true;
    } else if (mode === 'config') {
      const summary = target.MisconfSummary;
      if (target.Class !== 'config' || target.Type !== 'dockerfile' || !expectedTargets.includes(normalized(target.Target))
          || !summary || !['Successes', 'Failures'].every(k => Number.isSafeInteger(summary[k]) && summary[k] >= 0)
          || summary.Successes + summary.Failures === 0) fail();
      covered.add(normalized(target.Target));
    } else if (mode === 'fs' || mode === 'rootfs') {
      if (target.Class !== 'lang-pkgs' || !Array.isArray(target.Packages) || !target.Packages.length) fail();
      for (const expected of expectedArtifacts) {
        if (expected.kind === 'npm' && target.Type === 'npm' && normalized(target.Target) === expected.path) covered.add(expected.path);
        if (expected.kind === 'jar' && target.Type === 'jar') {
          const paths = target.Packages.map(pkg => normalized(pkg?.FilePath ?? pkg?.PkgPath));
          const required = expected.entries?.map(entry => expected.path + '/' + entry) ?? [expected.path];
          if (required.length && required.every(file => paths.some(p => p === file || p?.startsWith(file + '/')))) covered.add(expected.path);
        }
      }
    }
  }
  if (image && !os) fail();
  if (mode === 'config' && (!expectedTargets.length || new Set(expectedTargets).size !== expectedTargets.length
      || covered.size !== expectedTargets.length)) fail();
  if ((mode === 'fs' || mode === 'rootfs') && (!expectedArtifacts.length || new Set(expectedArtifacts.map(a => a.path)).size !== expectedArtifacts.length
      || covered.size !== expectedArtifacts.length)) fail();
}

export function runTrivy(args, run = spawnSync, log = console.log, expected = {}) {
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^TRIVY_/i.test(key)));
  const result = run(toolPath('trivy'), [...args, '--format', 'json', '--ignorefile', ''],
    { env, encoding: 'utf8', windowsHide: true, timeout: 360_000, maxBuffer: 32 * 1024 * 1024 });
  const fail = (message, category, stage) => Object.assign(new Error(message), { category, stage });
  if (result.error || ![0, 1].includes(result.status)) {
    const stderr = String(result.stderr ?? '');
    const timeout = result.error?.code === 'ETIMEDOUT' || /context deadline exceeded|timeout/i.test(stderr);
    const database = /(?:vulnerability|java)\s*(?:DB|database)|trivy-(?:java-)?db/i.test(stderr);
    const registry = /(?:registry|manifest|image|pull).*(?:failed|error|denied)|(?:failed|error).*(?:registry|manifest|image|pull)/i.test(stderr);
    throw fail('Trivy execution failed; raw output withheld', timeout ? 'TIMEOUT' : database || registry ? 'DEPENDENCY_ERROR' : 'SCANNER_ERROR',
      timeout ? 'timeout' : database ? 'vulnerability-db' : registry ? 'image-registry' : 'runtime');
  }
  let report;
  try { report = JSON.parse(result.stdout); } catch {
    const stderr = String(result.stderr ?? '');
    if (/context deadline exceeded|timeout/i.test(stderr)) throw fail('Trivy timeout; raw output withheld', 'TIMEOUT', 'timeout');
    if (/(?:vulnerability|java)\s*(?:DB|database)|trivy-(?:java-)?db/i.test(stderr)) throw fail('Trivy database unavailable', 'DEPENDENCY_ERROR', 'vulnerability-db');
    if (/(?:registry|manifest|image|pull).*(?:failed|error|denied)|(?:failed|error).*(?:registry|manifest|image|pull)/i.test(stderr)) throw fail('Trivy image unavailable', 'DEPENDENCY_ERROR', 'image-registry');
    throw fail('Trivy report invalid; raw output withheld', 'SCANNER_ERROR', 'json');
  }
  scannerContract(report, args, expected);
  const image = args[0] === 'image' ? args.at(-1) : null;
  const imageMetadata = image ? { image, status: 'CLEAN', high: 0, critical: 0, total: 0, truncated: false, vulnerabilities: [] } : null;
  if (image && !/^[a-z0-9][a-z0-9./_:-]*(?:@sha256:[a-f0-9]{64})?$/.test(image)) throw fail('Invalid image reference', 'SCANNER_ERROR', 'json');
  let findings = 0;
  for (const target of report.Results) {
    if (image) {
      if (target.Vulnerabilities !== undefined && !Array.isArray(target.Vulnerabilities)) throw fail('Invalid vulnerabilities', 'SCANNER_ERROR', 'json');
      for (const finding of target.Vulnerabilities ?? []) {
        if (!finding || typeof finding !== 'object') throw fail('Invalid vulnerability', 'SCANNER_ERROR', 'json');
        const entry = { VulnerabilityID: finding.VulnerabilityID, Severity: finding.Severity, PkgName: finding.PkgName, InstalledVersion: finding.InstalledVersion };
        if (!/^CVE-\d{4}-\d{4,}$|^GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}$/.test(entry.VulnerabilityID ?? '')
            || !['HIGH', 'CRITICAL'].includes(entry.Severity)
            || [entry.PkgName, entry.InstalledVersion].some(value => typeof value !== 'string' || !/^[A-Za-z0-9+._:/@,~ ()-]{1,200}$/.test(value))) throw fail('Unsafe/incomplete vulnerability metadata', 'SCANNER_ERROR', 'json');
        if (finding.FixedVersion !== undefined && finding.FixedVersion !== '') {
          if (typeof finding.FixedVersion !== 'string' || !/^[A-Za-z0-9+._:/@,~ ()-]{1,200}$/.test(finding.FixedVersion)) throw fail('Unsafe fix metadata', 'SCANNER_ERROR', 'json');
          entry.FixedVersion = finding.FixedVersion;
        }
        imageMetadata.vulnerabilities.push(entry);
        imageMetadata[entry.Severity === 'HIGH' ? 'high' : 'critical']++;
      }
      continue;
    }
    for (const finding of [...(target.Misconfigurations ?? []), ...(target.Vulnerabilities ?? [])]) {
      if (!finding || typeof finding !== 'object') throw fail('Invalid finding', 'SCANNER_ERROR', 'json');
      if (target.Class === 'config' && ['PASS', 'EXCEPTION'].includes(finding.Status)) continue;
      if (target.Class === 'config' && finding.Status !== 'FAIL') throw fail('Invalid misconfiguration status', 'SCANNER_ERROR', 'json');
      findings++;
      // Never print source excerpts, titles, descriptions, environment values or fixes.
      const id = finding.ID ?? finding.VulnerabilityID;
      const metadata = { tool: 'Trivy', target: String(target.Target),
        id: typeof id === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(id) ? id : 'unknown',
        severity: ['UNKNOWN', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(finding.Severity) ? finding.Severity : 'UNKNOWN' };
      if (Number.isInteger(finding.CauseMetadata?.StartLine)) metadata.line = finding.CauseMetadata.StartLine;
      for (const [field, value] of [['package', finding.PkgName], ['installed', finding.InstalledVersion], ['fixed', finding.FixedVersion]]) {
        if (typeof value === 'string' && /^[A-Za-z0-9+._:/@,~ -]{1,200}$/.test(value)) metadata[field] = value;
      }
      log(JSON.stringify(metadata));
    }
  }
  if (imageMetadata) {
    imageMetadata.total = imageMetadata.vulnerabilities.length;
    if (imageMetadata.total) {
      imageMetadata.status = 'FINDINGS';
      throw Object.assign(fail('Trivy findings require review', 'SECURITY_FINDINGS', 'findings'), { imageMetadata });
    }
    if (result.status !== 0) throw fail('Trivy nonzero without findings', 'SCANNER_ERROR', 'exit-without-findings');
    return imageMetadata;
  }
  log('Trivy: ' + report.Results.length + ' result entries; ' + findings + ' findings; raw output withheld');
  if (findings > 0) throw fail('Trivy findings require review', 'SECURITY_FINDINGS', 'findings');
  if (result.status !== 0) throw fail('Trivy nonzero without findings', 'SCANNER_ERROR', 'exit-without-findings');
}

export function dockerfileTargets(root) {
  const visit = (dir, prefix = '') => fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    if (entry.isSymbolicLink()) throw new Error('Scanner source link refused');
    const name = prefix + entry.name;
    return entry.isDirectory() ? visit(path.join(dir, entry.name), name + '/') : entry.name === 'Dockerfile' ? [name] : [];
  });
  return visit(root).sort();
}

export function removeSnapshot(destination) {
  const target = path.resolve(destination);
  if (path.dirname(target) !== path.resolve(os.tmpdir()) || !/^ltz-lint-[a-zA-Z0-9]+$/.test(path.basename(target))) throw new Error('Unsafe snapshot cleanup');
  fs.rmSync(target, { recursive: true, force: true });
}
export function sourceSnapshot(root = repositoryRoot, list = spawnSync) {
  const result = list('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: root, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
  if (result.status !== 0) throw new Error('Cannot enumerate source files');
  const destination = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-lint-'));
  try {
    for (const file of new Set(result.stdout.split('\0').filter(Boolean))) {
      if (sensitiveFile(file)) throw new Error('Sensitive source filename refused');
      const source = path.resolve(root, file), target = path.resolve(destination, file);
      if (!source.startsWith(path.resolve(root) + path.sep) || !target.startsWith(destination + path.sep)) throw new Error('Unsafe source path');
      try { fs.lstatSync(source); }
      catch (error) { if (error.code === 'ENOENT') continue; throw error; }
      let current = source;
      while (current !== path.resolve(root)) {
        if (fs.lstatSync(current).isSymbolicLink()) throw new Error('Source symlink refused');
        current = path.dirname(current);
      }
      if (!fs.statSync(source).isFile()) throw new Error('Unsupported source entry');
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.copyFileSync(source, target);
    }
    return destination;
  } catch (error) { removeSnapshot(destination); throw error; }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let snapshot;
  try {
    snapshot = sourceSnapshot();
    const mode = process.argv[2];
    let result;
    if (mode === 'trivy') {
      runTrivy(['config', '--config', path.join(repositoryRoot, 'pre-push/ci/trivy.yaml'),
        '--misconfig-scanners', 'dockerfile', '--include-non-failures', '--severity', 'HIGH,CRITICAL', '--exit-code', '1', snapshot],
      spawnSync, console.log, { expectedTargets: dockerfileTargets(snapshot) });
      result = { status: 0 };
    } else if (mode === 'mega') {
      result = spawnSync('docker', ['run', '--rm', '-v', snapshot + ':/tmp/lint:ro',
        '-e', 'REPORT_OUTPUT_FOLDER=/tmp/megalinter-reports', '-e', 'APPLY_FIXES=none',
        'ghcr.io/oxsecurity/megalinter:v10.1.0'], { encoding: 'utf8', windowsHide: true, timeout: 600_000, maxBuffer: 32 * 1024 * 1024 });
      console.log('MegaLinter pilot: ' + (result.status === 0 && !result.error ? 'PASS' : 'FAILED / NOT VERIFIED') + '; raw source logs withheld');
    } else throw new Error('Use trivy or mega');
    if (result.status !== 0 || result.error) throw new Error('Lint/scanner failed or could not run');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
  finally { if (snapshot) removeSnapshot(snapshot); }
}
