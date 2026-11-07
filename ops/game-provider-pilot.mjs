#!/usr/bin/env node
// Opt-in gerçek sağlayıcı pilotu; DEV DB yok, yalnız seçili sağlayıcının anahtarları JVM'e aktarılır.
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { isolatedEnvironment, captureCommand } from './hardware-auth-e2e.mjs';

export function providerValues(text) {
  const result = {};
  for (const raw of text.replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const match = raw.match(/^\s*(IGDB_CLIENT_ID|IGDB_CLIENT_SECRET|ITAD_API_KEY)\s*=\s*(.*?)\s*$/);
    if (!match) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    else value = value.replace(/\s+#.*$/, '').trim();
    result[match[1]] = value;
  }
  return result;
}

export function pilotConfiguration(values, provider = 'igdb') {
  if (!['igdb', 'itad'].includes(provider)) throw new Error('Bilinmeyen pilot sağlayıcısı.');
  if (provider === 'igdb' && (!values.IGDB_CLIENT_ID || !values.IGDB_CLIENT_SECRET)) throw new Error('IGDB kimlik bilgileri eksik; pilot çalıştırılmadı.');
  if (provider === 'itad' && !values.ITAD_API_KEY) throw new Error('ITAD anahtarı eksik; pilot çalıştırılmadı.');
  return { test: provider === 'itad' ? 'ITGamePriceLivePilot' : 'ITGameProviderLivePilot',
    environment: { IGDB_CLIENT_ID: provider === 'igdb' ? values.IGDB_CLIENT_ID : '',
      IGDB_CLIENT_SECRET: provider === 'igdb' ? values.IGDB_CLIENT_SECRET : '',
      ITAD_API_KEY: provider === 'itad' ? values.ITAD_API_KEY : '',
      GAME_CATALOG_SYNC_ENABLED: 'false', MAVEN_SKIP_RC: 'true' } };
}

export async function runProviderPilot(root, values, log = console.log, provider = 'igdb') {
  const configuration = pilotConfiguration(values, provider);
  const env = isolatedEnvironment(configuration.environment);
  const args = ['-o', '-B', '-f', 'backend/pom.xml', '-pl', 'services/game-service', `-Dtest=${configuration.test}`, '-Dltz.game.live-pilot=true', 'test'];
  const child = process.platform === 'win32'
    ? spawn(env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'mvn.cmd', ...args], { cwd: root, env, windowsHide: true })
    : spawn('mvn', args, { cwd: root, env });
  await captureCommand(child, `Game ${provider.toUpperCase()} pilot`, [values.IGDB_CLIENT_ID, values.IGDB_CLIENT_SECRET, values.ITAD_API_KEY]);
  log(`[PASS] Gerçek ${provider.toUpperCase()} → geçici PostgreSQL → Game public API; TTL tekrar okuması doğrulandı.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  try {
    if (process.argv[2] !== '--allow-env-files' || ![3, 4].includes(process.argv.length) || process.argv.length === 4 && process.argv[3] !== '--itad') throw new Error('ENV okuması için açık izin gerekli: --allow-env-files [--itad]');
    const values = { ...providerValues(fs.readFileSync(path.join(root, '.env'), 'utf8')), ...providerValues(fs.readFileSync(path.join(root, '.env.dev'), 'utf8')) };
    await runProviderPilot(root, values, console.log, process.argv[3] === '--itad' ? 'itad' : 'igdb');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
