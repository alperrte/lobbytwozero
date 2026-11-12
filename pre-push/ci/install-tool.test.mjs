import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { downloadArchive, installTool } from './install-tool.mjs';

const url = 'https://github.com/example/tool/releases/download/v1/tool.zip';

test('transient HTTP and connection failures retry with bounded backoff and fresh timeout signals', async () => {
  const logs = [], delays = [], signals = [];
  let calls = 0;
  const bytes = await downloadArchive(url, {
    fetcher: async (requested, options) => {
      assert.equal(requested, url);
      signals.push(options.signal);
      calls++;
      if (calls === 1) return new Response('PRIVATE_RESPONSE_BODY', { status: 503 });
      if (calls === 2) throw new TypeError('PRIVATE_ERROR_URL', { cause: { code: 'ECONNRESET' } });
      return new Response('archive');
    },
    wait: async delay => delays.push(delay), log: line => logs.push(line),
  });
  assert.equal(bytes.toString(), 'archive');
  assert.equal(calls, 3);
  assert.deepEqual(delays, [1000, 2000]);
  assert.equal(new Set(signals).size, 3);
  assert.match(logs.join('\n'), /503/);
  assert.match(logs.join('\n'), /ECONNRESET/);
  assert.ok(!logs.join('\n').includes('PRIVATE'));
});

test('permanent HTTP errors do not retry and expose only the safe status', async () => {
  for (const status of [401, 403, 404]) {
    let calls = 0;
    await assert.rejects(downloadArchive(url, {
      fetcher: async () => { calls++; return new Response('PRIVATE_BODY', { status }); },
      wait: async () => assert.fail('Permanent error must not retry'), log: () => assert.fail('No retry log expected'),
    }), error => error.category === 'DEPENDENCY_ERROR' && error.stage === 'download'
      && error.httpStatus === status && error.attempts === 1 && !error.message.includes('PRIVATE'));
    assert.equal(calls, 1);
  }
});

test('rate limits honor Retry-After only within the five-second delay cap', async () => {
  for (const response of [
    () => new Response('', { status: 429, headers: { 'retry-after': '100' } }),
    () => new Response('', { status: 403, headers: { 'retry-after': '100', 'x-ratelimit-remaining': '0' } }),
  ]) {
    let calls = 0;
    const delays = [];
    await downloadArchive(url, {
      fetcher: async () => ++calls === 1 ? response() : new Response('verified-later'),
      wait: async delay => delays.push(delay), log: () => {},
    });
    assert.deepEqual(delays, [5000]);
  }
});

test('persistent service outages and timeouts fail after exactly three attempts', async () => {
  for (const timeout of [false, true]) {
    let calls = 0;
    const delays = [];
    await assert.rejects(downloadArchive(url, {
      fetcher: async () => {
        calls++;
        if (timeout) throw Object.assign(new Error('PRIVATE_DETAIL'), { name: 'TimeoutError' });
        return new Response('PRIVATE_BODY', { status: 502 });
      },
      wait: async delay => delays.push(delay), log: () => {},
    }), error => error.category === 'DEPENDENCY_ERROR' && error.attempts === 3
      && error.reason === (timeout ? 'TIMEOUT' : 'HTTP_ERROR') && !error.message.includes('PRIVATE'));
    assert.equal(calls, 3);
    assert.deepEqual(delays, [1000, 2000]);
  }
});

test('interrupted archive bodies are retried but local access failures are not', async () => {
  let calls = 0;
  const bytes = await downloadArchive(url, {
    fetcher: async () => ++calls === 1 ? {
      status: 200, ok: true,
      arrayBuffer: async () => { throw new TypeError('PRIVATE_DETAIL', { cause: { code: 'UND_ERR_SOCKET' } }); },
    } : new Response('complete'),
    wait: async () => {}, log: () => {},
  });
  assert.equal(bytes.toString(), 'complete');
  await assert.rejects(downloadArchive(url, {
    fetcher: async () => { throw new TypeError('PRIVATE_DETAIL', { cause: { code: 'EACCES' } }); },
    wait: async () => assert.fail('Access error must not retry'), log: () => {},
  }), error => error.attempts === 1);
});

test('a downloaded archive with a wrong pinned hash is never written or extracted', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-tool-integrity-'));
  let calls = 0;
  try {
    await assert.rejects(installTool('prek', {
      root, fetcher: async () => { calls++; return new Response('tampered archive'); },
      wait: async () => assert.fail('Hash failure must not retry'), log: () => assert.fail('No success log permitted'),
    }), error => error.category === 'CONFIGURATION_ERROR' && error.stage === 'integrity');
    assert.equal(calls, 1);
    assert.deepEqual(fs.readdirSync(root), []);
  } finally {
    fs.rmdirSync(root);
  }
});
