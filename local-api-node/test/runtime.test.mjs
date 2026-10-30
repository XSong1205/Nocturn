import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs/promises';
import path from 'node:path';
import { startRuntime } from '../scripts/runtime-helper.mjs';

test('actual bundled provider starts offline, writes readiness and handles a local QR create request', async (t) => {
  const runtime = await startRuntime();
  t.after(runtime.close);
  const readyFile = JSON.parse(await fs.readFile(path.join(runtime.dataDirectory, 'runtime-ready.json'), 'utf8'));
  assert.deepEqual(readyFile, runtime.ready);
  assert(!JSON.stringify(readyFile).includes(runtime.token));
  const health = await fetch(`${runtime.url}/health`);
  assert.equal(health.status, 200);
  assert.equal((await health.json()).status, 'ready');
  const response = await fetch(`${runtime.url}/api/login/qr/create`, {
    method: 'POST',
    headers: { authorization: `Bearer ${runtime.token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ key: 'nocturn_offline_contract_key', qrimg: false }),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    code: 200,
    data: { qrurl: 'https://music.163.com/login?codekey=nocturn_offline_contract_key', qrimg: '' },
  });
  assert.equal((await fs.readFile(path.join(runtime.dataDirectory, 'device-id'), 'utf8')).length, 52);
});
