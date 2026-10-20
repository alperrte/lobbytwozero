import fs from 'node:fs';
import path from 'node:path';

// Explicit profile only: never execute ENV contents or blend in the active .env.
export function loadProfileEnv(root, profile, ambient = process.env) {
  if (!['dev', 'prod'].includes(profile)) throw new Error('Profile must be dev or prod');
  const filename = path.join(root, `.env.${profile}`);
  if (!fs.existsSync(filename)) throw new Error(`Missing .env.${profile}; create it from .env.example`);
  const values = {};
  const allowed = new Set(['SONAR_HOST_URL', 'SONAR_TOKEN', 'SONAR_BACKEND_PROJECT_KEY', 'SONAR_FRONTEND_PROJECT_KEY',
    'SONARQUBE_HOST_PORT', 'SONARQUBE_INTERNAL_PORT', 'SONAR_DB_NAME', 'SONAR_DB_USER', 'SONAR_DB_PASSWORD']);
  for (const line of fs.readFileSync(filename, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match || !allowed.has(match[1])) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    else value = value.replace(/\s+#.*$/, '').trim();
    values[match[1]] = value;
  }
  return { ...values, ...ambient };
}

export function requireNode() {
  const [major, minor] = process.versions.node.split('.').map(Number);
  if (major < 22 || (major === 22 && minor < 12)) throw new Error('Node.js 22.12+ is required');
}
