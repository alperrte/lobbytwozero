import fs from 'node:fs';
import path from 'node:path';

// Explicit profile only: never execute ENV contents or blend in the active .env.
function readProfileValues(root, profile, allowed) {
  if (!['dev', 'prod'].includes(profile)) throw new Error('Profile must be dev or prod');
  const filename = path.join(root, `.env.${profile}`);
  if (!fs.existsSync(filename)) throw new Error(`Missing .env.${profile}; create it from .env.example`);
  const values = {};
  for (const line of fs.readFileSync(filename, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match || !allowed.has(match[1])) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    else value = value.replace(/\s+#.*$/, '').trim();
    values[match[1]] = value;
  }
  return values;
}

export function loadProfileEnv(root, profile, ambient = process.env) {
  const allowed = new Set(['SONAR_HOST_URL', 'SONAR_TOKEN', 'SONAR_BACKEND_PROJECT_KEY', 'SONAR_FRONTEND_PROJECT_KEY',
    'SONARQUBE_HOST_PORT', 'SONARQUBE_INTERNAL_PORT', 'SONAR_DB_NAME', 'SONAR_DB_USER', 'SONAR_DB_PASSWORD']);
  return { ...readProfileValues(root, profile, allowed), ...ambient };
}

// Only host Maven test processes receive these existing application DB settings.
// Profile POSTGRES_HOST/PORT are container addresses and deliberately not loaded.
export function loadBackendTestEnv(root, profile, ambient = process.env) {
  const allowed = new Set(['POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_HOST_PORT',
    'DB_AUTH_NAME', 'DB_USER_NAME', 'DB_MAIL_NAME', 'DB_GAME_NAME', 'DB_LIBRARY_NAME',
    'DB_REVIEW_NAME', 'DB_SOCIAL_NAME', 'DB_MESSAGING_NAME', 'DB_LFP_NAME',
    'DB_COMMUNITY_NAME', 'DB_CONTENT_NAME', 'DB_EVENT_NAME', 'DB_HARDWARE_NAME',
    'DB_RECOMMENDATION_NAME', 'DB_PERFORMANCE_NAME', 'DB_ADMIN_NAME', 'DB_NOTIFICATION_NAME']);
  const env = { ...readProfileValues(root, profile, allowed), ...ambient };
  if (!env.POSTGRES_USER?.trim() || !env.POSTGRES_PASSWORD?.trim()) {
    throw new Error('Backend tests require POSTGRES_USER and POSTGRES_PASSWORD in the selected profile or terminal');
  }
  const port = ambient.POSTGRES_PORT || env.POSTGRES_HOST_PORT || '5542';
  if (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535) {
    throw new Error('Backend test PostgreSQL port must be between 1 and 65535');
  }
  return { ...env, POSTGRES_HOST: ambient.POSTGRES_HOST || '127.0.0.1', POSTGRES_PORT: port };
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
