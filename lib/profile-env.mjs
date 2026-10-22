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

// Host üzerinde koşan backend testleri (Maven) için yalnız veritabanı bağlantı ayarlarını seçilen profilden alır.
// Profil dosyasının tamamı yüklenmez: SPRING_PROFILES_ACTIVE, JWT/TOTP anahtarları gibi değişkenler testlere sızmaz.
// POSTGRES_HOST dosyada Docker ağı adıdır (postgres); ana makinedeki testler yayınlanan loopback portuna bağlanır.
// Terminal değişkenleri önceliklidir (POSTGRES_HOST/POSTGRES_PORT hariç: her zaman loopback). Dosya yoksa ortam aynen döner.
const BACKEND_TEST_KEYS = /^(POSTGRES_USER|POSTGRES_PASSWORD|POSTGRES_HOST_PORT|DB_[A-Z0-9_]+_NAME)$/;

export function backendTestEnv(root, profile, ambient = process.env) {
  const filename = path.join(root, `.env.${profile}`);
  if (!['dev', 'prod'].includes(profile) || !fs.existsSync(filename)) return { ...ambient };
  const values = {};
  for (const line of fs.readFileSync(filename, 'utf8').replace(/^﻿/, '').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match || !BACKEND_TEST_KEYS.test(match[1])) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    else value = value.replace(/\s+#.*$/, '').trim();
    values[match[1]] = value;
  }
  const env = { ...values, ...ambient };
  env.POSTGRES_HOST = 'localhost';
  env.POSTGRES_PORT = values.POSTGRES_HOST_PORT || ambient.POSTGRES_HOST_PORT || '5542';
  return env;
}
