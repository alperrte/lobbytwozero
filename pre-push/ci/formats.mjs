import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { repositoryRoot } from '../../lib/quality-tools.mjs';
import { sensitiveFile } from '../runtime-checks.mjs';
import { toolPath } from './install-tool.mjs';

export function runTrivy(args, run = spawnSync, log = console.log) {
  const result = run(toolPath('trivy'), [...args, '--format', 'json', '--ignorefile', ''],
    { encoding: 'utf8', windowsHide: true, timeout: 360_000, maxBuffer: 32 * 1024 * 1024 });
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
  if (!Array.isArray(report.Results) || report.Results.length === 0) throw fail('Trivy target coverage unverified', 'SCANNER_ERROR', 'target-coverage');
  const image = args[0] === 'image' ? args.at(-1) : null;
  const imageMetadata = image ? { image, status: 'CLEAN', high: 0, critical: 0, total: 0, truncated: false, vulnerabilities: [] } : null;
  if (image && !/^[a-z0-9][a-z0-9./_:-]*(?:@sha256:[a-f0-9]{64})?$/.test(image)) throw fail('Invalid image reference', 'SCANNER_ERROR', 'json');
  let findings = 0;
  for (const target of report.Results) {
    if (image) {
      if (target.Vulnerabilities !== undefined && !Array.isArray(target.Vulnerabilities)) throw fail('Invalid vulnerabilities', 'SCANNER_ERROR', 'json');
      for (const finding of target.Vulnerabilities ?? []) {
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
        '--misconfig-scanners', 'dockerfile', '--severity', 'HIGH,CRITICAL', '--exit-code', '1', snapshot]);
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
