import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { backendTestEnv } from '../lib/profile-env.mjs';

function root(files) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-backend-env-'));
  for (const [name, content] of Object.entries(files)) fs.writeFileSync(path.join(dir, name), content);
  return dir;
}

const PROFILE = [
  'SPRING_PROFILES_ACTIVE=dev',
  'POSTGRES_HOST=postgres',
  'POSTGRES_PORT=5432',
  'POSTGRES_HOST_PORT=5544',
  'POSTGRES_USER=ltz_user',
  'POSTGRES_PASSWORD="profil-parolasi"',
  'DB_AUTH_NAME=auth_custom',
  'AUTH_JWT_PRIVATE_KEY=gizli-anahtar',
  'SONAR_TOKEN=gizli-token',
  '',
].join('\r\n');

test('yalnız veritabanı ayarları yüklenir; profil, anahtar ve token sızmaz; host loopback olur', () => {
  const dir = root({ '.env.dev': PROFILE });
  try {
    const env = backendTestEnv(dir, 'dev', { PATH: 'x' });
    assert.equal(env.POSTGRES_USER, 'ltz_user');
    assert.equal(env.POSTGRES_PASSWORD, 'profil-parolasi');
    assert.equal(env.DB_AUTH_NAME, 'auth_custom');
    assert.equal(env.POSTGRES_HOST, 'localhost');
    assert.equal(env.POSTGRES_PORT, '5544');
    assert.equal(env.PATH, 'x');
    for (const leaked of ['SPRING_PROFILES_ACTIVE', 'AUTH_JWT_PRIVATE_KEY', 'SONAR_TOKEN', 'POSTGRES_HOST_PORT']) {
      if (leaked === 'POSTGRES_HOST_PORT') continue; // yalnız port eşlemesi için okunur, zararsız
      assert.equal(env[leaked], undefined, leaked + ' sızmamalı');
    }
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('terminal değişkenleri önceliklidir ama POSTGRES_HOST ana makinede her zaman loopback olur', () => {
  const dir = root({ '.env.dev': PROFILE });
  try {
    const env = backendTestEnv(dir, 'dev', { POSTGRES_PASSWORD: 'terminal-parolasi', POSTGRES_HOST: 'postgres', POSTGRES_PORT: '5432' });
    assert.equal(env.POSTGRES_PASSWORD, 'terminal-parolasi');
    assert.equal(env.POSTGRES_HOST, 'localhost');
    assert.equal(env.POSTGRES_PORT, '5544');
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('profil dosyası yoksa veya profil geçersizse ortam aynen döner; port varsayılanı yalnız dosya varken uygulanır', () => {
  const dir = root({});
  try {
    assert.deepEqual(backendTestEnv(dir, 'dev', { A: '1' }), { A: '1' });
    assert.deepEqual(backendTestEnv(dir, 'staging', { A: '1' }), { A: '1' });
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
  const noPort = root({ '.env.dev': 'POSTGRES_USER=u\nPOSTGRES_PASSWORD=p\n' });
  try { assert.equal(backendTestEnv(noPort, 'dev', {}).POSTGRES_PORT, '5542'); }
  finally { fs.rmSync(noPort, { recursive: true, force: true }); }
});
