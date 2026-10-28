import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { sha } from './secrets.mjs';
import { readModules, repositoryRoot } from '../../lib/quality-tools.mjs';

export function selectScope(files, available = readModules(repositoryRoot)) {
  const scope = { backend: false, frontend: false, config: false, modules: [] };
  const selected = new Set();
  let allBackend = false;
  const policy = /^(?:(?:AGENTS|CLAUDE)\.md|docs\/(?:SECURITY|engineering|folder-structure|web-rules|architecture|database)\.md|docs\/decisions\/|\.github\/|\.mvn\/|pre-push\/|lib\/)/;
  for (const file of files) {
    if (policy.test(file)) {
      Object.assign(scope, { backend: true, frontend: true, config: true });
      allBackend = true;
      continue;
    }
    if (/^(?:docs\/.*\.(?:md|mdx|svg)|README\.md)$/.test(file)) continue;
    const service = file.match(/^backend\/(services\/[a-z][a-z0-9-]*)\/(.*)$/);
    if (service) {
      scope.backend = true;
      if (!available.includes(service[1])) { allBackend = true; scope.config = true; continue; }
      selected.add(service[1]);
      if (/Dockerfile$/.test(file)) scope.config = true;
      // Services reach each other only through their public API and the gateway. Only changes another service can
      // feel expand to the whole backend: the gateway, a service pom.xml and the exposed api/ package (controllers,
      // DTOs). Internal code, own configuration and own migrations (one database per service) stay in their module,
      // so one team's pull request is never judged by another team's modules.
      if (service[1] === 'services/api-gateway' || service[2] === 'pom.xml'
        || /^src\/main\/java\/.*\/api\//.test(service[2])) allBackend = true;
    } else if (file.startsWith('backend/')) {
      scope.backend = true; allBackend = true;
    } else if (file.startsWith('frontend/')) scope.frontend = true;
    else if (/^(?:docker-compose.*\.ya?ml|\.dockerignore|postgres\/)/.test(file)) {
      scope.backend = true; scope.config = true; allBackend = true;
    } else {
      Object.assign(scope, { backend: true, frontend: true, config: true });
      allBackend = true;
    }
  }
  scope.modules = allBackend ? [...available] : available.filter(module => selected.has(module));
  return scope;
}
export function gatePassed(scope, results) {
  if (!scope || !['backend', 'frontend', 'config'].every(key => typeof scope[key] === 'boolean')
    || !Array.isArray(scope.modules) || new Set(scope.modules).size !== scope.modules.length
    || scope.modules.some(module => !/^services\/[a-z][a-z0-9-]*$/.test(module))
    || (scope.backend ? scope.modules.length === 0 : scope.modules.length !== 0)) return false;
  return ['scope', 'secrets', 'node', 'hooks'].every(key => results[key] === 'success')
    && ['backend', 'frontend', 'config'].every(key => scope[key] ? results[key] === 'success' : ['success', 'skipped'].includes(results[key]));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv[2] === 'gate') {
      const scope = JSON.parse(process.env.SCOPE_JSON);
      const results = JSON.parse(process.env.JOB_RESULTS);
      if (!gatePassed(scope, results)) throw new Error('Required job failed, cancelled, missing or unexpectedly skipped');
      console.log('Quality Gate: PASS');
    } else {
      const [base, head] = process.argv.slice(2);
      if (!sha(base) || !sha(head)) throw new Error('Full commit SHAs required');
      const diff = spawnSync('git', ['diff', '--name-only', '--no-renames', '-z', base, head, '--'], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
      if (diff.status !== 0) throw new Error('Cannot determine changed files');
      const scope = selectScope(diff.stdout.split('\0').filter(Boolean));
      const lines = Object.entries(scope).map(([key, value]) => key + '=' + value);
      lines.push('json=' + JSON.stringify(scope));
      if (!process.env.GITHUB_OUTPUT) throw new Error('GITHUB_OUTPUT required');
      fs.appendFileSync(process.env.GITHUB_OUTPUT, lines.join('\n') + '\n');
      console.log('Selected scope: ' + JSON.stringify(scope));
    }
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
