import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { startRuntime } from '../scripts/runtime-helper.mjs';

const bundleRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'dist');

test('Android bundle resolves upstream temp state through NOCTURN_DATA_DIR instead of /tmp', async () => {
  const bundle = await fs.readFile(path.join(bundleRoot, 'main.cjs'), 'utf8');
  // Upstream reads os.tmpdir() at module load; Android has no /tmp. The adapter must rewrite
  // every such read to the app-private data directory while keeping the host fallback.
  assert.match(bundle, /process\.env\.NOCTURN_DATA_DIR \|\| require\("os"\)\.tmpdir\(\)/);
  assert.doesNotMatch(bundle, /const tmpPath = require\("os"\)\.tmpdir\(\)/);
  // Startup diagnostics remain app-private instead of being written to logcat.
  assert.match(bundle, /startup-error\.log/);
});

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
