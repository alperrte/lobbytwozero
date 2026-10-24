import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { readModules, repositoryRoot } from '../../lib/quality-tools.mjs';
import { runTrivy } from './formats.mjs';

function plainFile(root, relative) {
  const file = path.resolve(root, relative), base = path.resolve(root);
  if (!file.startsWith(base + path.sep)) throw new Error('Dependency source path refused');
  for (let current = file; current !== base; current = path.dirname(current)) {
    if (fs.lstatSync(current).isSymbolicLink()) throw new Error('Dependency source link refused');
  }
  if (!fs.statSync(file).isFile()) throw new Error('Dependency source is not a regular file');
  return file;
}

// Scan packaged runtime dependencies, never a POM-only approximation or the developer's Maven cache.
export function dependencyCheck({ root = repositoryRoot, modules = [], frontend = false, since,
  run = spawnSync, scan = runTrivy, log = console.log } = {}) {
  const available = readModules(root);
  if (!Array.isArray(modules) || new Set(modules).size !== modules.length || modules.some(m => !available.includes(m))
      || typeof frontend !== 'boolean' || (!modules.length && !frontend)) throw new Error('Invalid dependency scan scope');
  if (modules.length && (!Number.isSafeInteger(since) || since <= 0 || since > Date.now())) throw new Error('Fresh build timestamp required');
  const destination = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-dependencies-'));
  const expectedArtifacts = [];
  const copy = (source, relative, kind) => {
    const target = path.join(destination, relative);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(source, target);
    expectedArtifacts.push({ path: relative, kind });
  };
  try {
    for (const module of modules) {
      const target = path.join(root, 'backend', module, 'target');
      // Includes links in the inventory so a linked archive cannot disappear silently.
      const jars = fs.readdirSync(target).filter(name => name.endsWith('.jar'));
      if (jars.length !== 1) throw new Error('Exactly one packaged application JAR required: ' + module);
      const source = plainFile(root, 'backend/' + module + '/target/' + jars[0]);
      const stat = fs.statSync(source);
      if (stat.mtimeMs < since - 2000 || stat.size < 4) throw new Error('Stale/empty application archive: ' + module);
      const signature = Buffer.alloc(4), fd = fs.openSync(source, 'r');
      try { fs.readSync(fd, signature, 0, 4, 0); } finally { fs.closeSync(fd); }
      if (!signature.equals(Buffer.from([0x50, 0x4b, 0x03, 0x04]))) throw new Error('Invalid application archive: ' + module);
      const jar = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar') : 'jar';
      const env = Object.fromEntries(['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'JAVA_HOME']
        .filter(key => process.env[key]).map(key => [key, process.env[key]]));
      const listing = run(jar, ['--list', '--file', source], { env, encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
      if (listing.error || listing.status !== 0) throw new Error('Application archive inventory unavailable: ' + module);
      const entries = String(listing.stdout).split(/\r?\n/).filter(name => /^BOOT-INF\/lib\/[A-Za-z0-9_.+-]+\.jar$/.test(name));
      if (!entries.length || new Set(entries).size !== entries.length) throw new Error('Packaged runtime libraries missing/ambiguous: ' + module);
      copy(source, module + '/app.jar', 'jar');
      expectedArtifacts.at(-1).entries = entries;
    }
    if (frontend) {
      const lock = plainFile(root, 'frontend/package-lock.json');
      const parsed = JSON.parse(fs.readFileSync(lock, 'utf8'));
      if (![2, 3].includes(parsed.lockfileVersion) || !parsed.packages || Object.keys(parsed.packages).length < 2) throw new Error('Incomplete npm lockfile');
      copy(lock, 'frontend/package-lock.json', 'npm');
    }
    log('Dependency scan: ' + expectedArtifacts.map(a => a.path).join(', '));
    scan(['fs', '--scanners', 'vuln', '--pkg-types', 'library', '--list-all-pkgs',
      '--severity', 'HIGH,CRITICAL', '--exit-code', '1', destination], run, log, { expectedArtifacts });
    log('Dependency scan: PASS (complete requested package inventory; HIGH/CRITICAL gate)');
    return 0;
  } finally {
    if (path.dirname(destination) !== path.resolve(os.tmpdir()) || !/^ltz-dependencies-[a-zA-Z0-9]+$/.test(path.basename(destination))) throw new Error('Unsafe dependency cleanup');
    fs.rmSync(destination, { recursive: true, force: true });
  }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv[2] === 'frontend' && process.argv.length === 3) process.exitCode = dependencyCheck({ frontend: true });
    else if (process.argv[2] === 'backend' && process.argv.length === 4) {
      process.exitCode = dependencyCheck({ modules: readModules(repositoryRoot), since: Number(process.argv[3]) });
    } else throw new Error('Use dependencies.mjs frontend or backend <fresh-build-epoch-ms>');
  } catch (error) {
    console.error('Dependency scan FAILED / NOT VERIFIED: ' + (error.category ?? 'ARTIFACT_OR_RUNTIME_ERROR'));
    process.exitCode = 1;
  }
}
