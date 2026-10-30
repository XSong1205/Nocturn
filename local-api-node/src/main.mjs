import fs from 'node:fs';
import path from 'node:path';
import createProvider from './provider.cjs';
import { startLocalApi, VERSION } from './server.mjs';

export async function main() {
  const token = process.env.NOCTURN_API_TOKEN;
  const rawPort = process.env.NOCTURN_API_PORT || '0';
  if (!/^\d{1,5}$/.test(rawPort)) throw new Error('Invalid LocalAPI port.');
  const dataDir = process.env.NOCTURN_DATA_DIR;
  if (!dataDir || !path.isAbsolute(dataDir)) throw new Error('NOCTURN_DATA_DIR must be an absolute private directory.');
  fs.mkdirSync(dataDir, { recursive: true });
  process.env.TMPDIR = dataDir;
  process.env.TMP = dataDir;
  process.env.TEMP = dataDir;
  const instance = await startLocalApi({ token, port: Number(rawPort), provider: createProvider(dataDir) });
  const ready = JSON.stringify({ port: instance.port, version: VERSION });
  const temporaryReady = path.join(dataDir, 'runtime-ready.json.tmp');
  try {
    fs.writeFileSync(temporaryReady, ready, { mode: 0o600 });
    fs.renameSync(temporaryReady, path.join(dataDir, 'runtime-ready.json'));
  } catch (error) {
    await instance.close();
    throw error;
  }
  process.stdout.write(`NOCTURN_LOCAL_API_READY ${JSON.stringify({ port: instance.port, version: VERSION })}\n`);
  // In an embedded runtime process.exit() can terminate the Android host. Let Node's loop drain instead.
  const shutdown = () => {
    instance.server.close();
    instance.server.closeAllConnections?.();
    process.exitCode = 0;
  };
  process.once('SIGTERM', shutdown);
  process.once('SIGINT', shutdown);
  return instance;
}

main().catch(() => {
  // Error text from dependencies can contain app-private data; publish only a stable failure marker.
  process.stderr.write('NOCTURN_LOCAL_API_FAILED STARTUP_FAILURE\n');
  process.exitCode = 1;
});
