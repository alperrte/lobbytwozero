import test from 'node:test';
import assert from 'node:assert/strict';
import { pilotConfiguration, providerValues, runProviderPilot } from './game-provider-pilot.mjs';

test('pilot parser accepts only provider keys and never executes ENV expressions', () => {
  assert.deepEqual(providerValues('DB_PASSWORD=not-forwarded\nIGDB_CLIENT_ID="fixture-id"\nIGDB_CLIENT_SECRET=\'fixture-secret\'\nITAD_API_KEY=$(not-executed) # comment'), {
    IGDB_CLIENT_ID: 'fixture-id', IGDB_CLIENT_SECRET: 'fixture-secret', ITAD_API_KEY: '$(not-executed)'
  });
});
test('missing credentials are a visible failure before launching a process', async () => {
  await assert.rejects(runProviderPilot('.', {}), /kimlik bilgileri eksik/);
});

test('ITAD pilot forwards only the selected provider credential', () => {
  const config = pilotConfiguration({ IGDB_CLIENT_ID: 'unused-id', IGDB_CLIENT_SECRET: 'unused-secret', ITAD_API_KEY: 'fixture-key' }, 'itad');
  assert.equal(config.test, 'ITGamePriceLivePilot');
  assert.equal(config.environment.ITAD_API_KEY, 'fixture-key');
  assert.equal(config.environment.IGDB_CLIENT_ID, '');
  assert.equal(config.environment.IGDB_CLIENT_SECRET, '');
  assert.throws(() => pilotConfiguration({}, 'itad'), /ITAD anahtarı eksik/);
  assert.throws(() => pilotConfiguration({}, 'unknown'), /Bilinmeyen/);
});
