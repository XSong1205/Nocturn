import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

export async function startRuntime() {
  const dataDirectory = await fs.mkdtemp(path.join(os.tmpdir(), 'nocturn-local-api-'));
  const token = randomBytes(48).toString('base64url');
  const entry = fileURLToPath(new URL('../dist/main.cjs', import.meta.url));
  const child = spawn(process.execPath, [entry], {
    env: { ...process.env, NOCTURN_API_TOKEN: token, NOCTURN_API_PORT: '0', NOCTURN_DATA_DIR: dataDirectory },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  let errors = '';
  child.stderr.on('data', (chunk) => { errors += chunk.toString(); });
  const close = async () => {
    if (child.exitCode === null && child.signalCode === null) {
      await new Promise((resolve) => {
        const timer = setTimeout(() => { child.kill('SIGKILL'); resolve(); }, 3000);
        child.once('exit', () => { clearTimeout(timer); resolve(); });
        child.kill('SIGTERM');
      });
    }
    await fs.rm(dataDirectory, { recursive: true, force: true });
  };
  try {
    const ready = await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Built runtime startup timed out.')), 10000);
      let output = '';
      child.stdout.on('data', (chunk) => {
        output += chunk.toString();
        const marker = output.match(/^NOCTURN_LOCAL_API_READY (.+)$/m);
        if (marker) { clearTimeout(timeout); resolve(JSON.parse(marker[1])); }
      });
      child.once('error', (error) => { clearTimeout(timeout); reject(error); });
      child.once('exit', () => { clearTimeout(timeout); reject(new Error(`Built runtime exited before ready (${errors.trim() || 'no diagnostic marker'}).`)); });
    });
    return { child, token, dataDirectory, ready, url: `http://127.0.0.1:${ready.port}`, close };
  } catch (error) { await close(); throw error; }
}
