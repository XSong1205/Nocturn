import fs from 'node:fs';
import path from 'node:path';

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
  // The upstream API reads its private state (anonymous token, xeapi key, temp files) from
  // `os.tmpdir()` at module load. Load the provider only after the environment is configured,
  // and keep the bundled adapter pointing os.tmpdir() at the app-private data directory.
  const [{ default: createProvider }, { startLocalApi, VERSION }] = await Promise.all([
    import('./provider.cjs'),
    import('./server.mjs'),
  ]);
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

main().catch((error) => {
  // Error text from dependencies can contain app-private data; publish only a stable failure
  // marker to stdout/stderr. A redacted, app-private diagnostics file keeps the cause
  // retrievable on device without ever writing it to logcat.
  try {
    const dataDir = process.env.NOCTURN_DATA_DIR;
    if (dataDir) {
      const detail = error instanceof Error
        ? `${error.name}: ${error.message}\n${error.stack || ''}`
        : String(error);
      fs.writeFileSync(
        path.join(dataDir, 'startup-error.log'),
        `node=${process.version} arch=${process.arch}\n${detail}\n`,
        { mode: 0o600 },
      );
    }
  } catch {
    // Diagnostics are best-effort; the stable marker below is the contract.
  }
  process.stderr.write('NOCTURN_LOCAL_API_FAILED STARTUP_FAILURE\n');
  process.exitCode = 1;
});
