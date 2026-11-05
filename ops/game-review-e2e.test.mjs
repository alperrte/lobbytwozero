import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { fixtureToken } from './game-review-e2e.mjs';

test('isolated fixture tokens have verifiable RS256 signatures and bounded service identity claims', () => {
  const keys = generateKeyPairSync('rsa', { modulusLength: 3072 });
  const token = fixtureToken(keys.privateKey, 'fixture-key', 'svc:auth-service', {
    aud: ['review-service'], scope: 'account:erase', account_id: '00000000-0000-4000-8000-000000000001',
  }, 'svc+jwt');
  const [header, payload, signature] = token.split('.');
  assert.equal(verify('RSA-SHA256', Buffer.from(header + '.' + payload), keys.publicKey, Buffer.from(signature, 'base64url')), true);
  const claims = JSON.parse(Buffer.from(payload, 'base64url'));
  assert.deepEqual(JSON.parse(Buffer.from(header, 'base64url')), { alg: 'RS256', typ: 'svc+jwt', kid: 'fixture-key' });
  assert.equal(claims.exp - claims.iat, 60);
  assert.equal(claims.sub, 'svc:auth-service');
  assert.deepEqual(claims.aud, ['review-service']);
  assert.equal(claims.scope, 'account:erase');
  assert.equal(claims.account_id, '00000000-0000-4000-8000-000000000001');
});
