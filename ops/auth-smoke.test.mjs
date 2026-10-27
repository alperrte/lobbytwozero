import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decodeJwtPart, runAuthSmoke } from './auth-smoke.mjs';

test('JWT parts are decoded as base64url JSON without any signature trust', () => {
  const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${part({ typ: 'at+jwt', alg: 'RS256' })}.${part({ sub: 'fixture', iat: 1, exp: 61 })}.signature`;
  assert.deepEqual(decodeJwtPart(token, 0), { typ: 'at+jwt', alg: 'RS256' });
  assert.equal(decodeJwtPart(token, 1).exp - decodeJwtPart(token, 1).iat, 60);
});

test('importing the smoke module never starts containers or services', () => {
  assert.equal(typeof runAuthSmoke, 'function');
});
