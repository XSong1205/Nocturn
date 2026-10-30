import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { build } from 'esbuild';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const apiRoot = path.join(root, 'node_modules/@neteasecloudmusicapienhanced/api');
const dist = path.join(root, 'dist');
const stage = path.resolve(root, '../local-api-android/build/generated/nodeAssets/nocturn-local-api');
await fs.mkdir(dist, { recursive: true });

const result = await build({
  entryPoints: [path.join(root, 'src/main.mjs')],
  outfile: path.join(dist, 'main.cjs'),
  bundle: true,
  platform: 'node',
  target: 'node18',
  format: 'cjs',
  sourcemap: false,
  minify: false,
  legalComments: 'eof',
  metafile: true,
  // The upstream library logs full responses in several paths. Do not ship these logs to Android.
  drop: ['console'],
  plugins: [{
    name: 'nocturn-mobile-adapter',
    setup(context) {
      // Explicitly unsupported optional upstream functionality. Avoid its dependencies entirely.
      context.onResolve({ filter: /^@neteasecloudmusicapienhanced\/unblockmusic-utils$/ }, () => ({ path: 'unblock-disabled', namespace: 'nocturn' }));
      context.onLoad({ filter: /.*/, namespace: 'nocturn' }, () => ({ contents: 'module.exports = { matchID: async () => { throw new Error("Unsupported capability"); } };', loader: 'js' }));
      context.onLoad({ filter: /\.js$/ }, async ({ path: sourcePath }) => {
        if (!sourcePath.startsWith(apiRoot + path.sep)) return undefined;
        const source = await fs.readFile(sourcePath, 'utf8');
        const relativeDir = path.relative(apiRoot, path.dirname(sourcePath)).split(path.sep).join('/');
        // Bundled modules otherwise lose their own __dirname. Preserve data-file locations under api/.
        const directory = `require("node:path").join(__dirname, "api", ${JSON.stringify(relativeDir)})`;
        return { contents: source.replace(/\b__dirname\b/g, directory), loader: 'js', resolveDir: path.dirname(sourcePath) };
      });
    },
  }],
});

await fs.mkdir(path.join(dist, 'api'), { recursive: true });
await fs.cp(path.join(apiRoot, 'data'), path.join(dist, 'api/data'), { recursive: true });
// Keep upstream and all bundled package license texts with the distributable assets.
const upstreamMetadata = JSON.parse(await fs.readFile(path.join(apiRoot, 'package.json'), 'utf8'));
const noticeParts = ['Nocturn LocalAPI: independent transport and adapter.', `NeteaseCloudMusicApiEnhanced ${upstreamMetadata.version} and bundled dependencies follow their respective licenses.`];
const packageDirectories = new Set();
for (const input of Object.keys(result.metafile.inputs)) {
  const absolute = path.resolve(input);
  const pieces = absolute.split(path.sep);
  const index = pieces.lastIndexOf('node_modules');
  if (index < 0) continue;
  const count = pieces[index + 1]?.startsWith('@') ? index + 3 : index + 2;
  packageDirectories.add(pieces.slice(0, count).join(path.sep));
}
for (const directory of [...packageDirectories].sort()) {
  const metadata = JSON.parse(await fs.readFile(path.join(directory, 'package.json'), 'utf8'));
  const names = await fs.readdir(directory);
  const licenses = names.filter((name) => /^(license|licence|copying|notice)(\.|$)/i.test(name));
  noticeParts.push(`\n--- ${metadata.name}@${metadata.version} (${metadata.license || 'see upstream'}) ---`);
  for (const filename of licenses) {
    const licensePath = path.join(directory, filename);
    if ((await fs.stat(licensePath)).isFile()) noticeParts.push(await fs.readFile(licensePath, 'utf8'));
  }
}
await fs.writeFile(path.join(dist, 'THIRD_PARTY_NOTICES.txt'), noticeParts.join('\n\n'));

async function listFiles(directory, relative = '') {
  const files = [];
  for (const entry of (await fs.readdir(directory, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) {
    const child = relative ? `${relative}/${entry.name}` : entry.name;
    if (entry.isDirectory()) files.push(...await listFiles(path.join(directory, entry.name), child));
    else if (child !== 'manifest.json') files.push(child);
  }
  return files;
}
const files = await listFiles(dist);
const sha256 = {};
for (const file of files) sha256[file] = createHash('sha256').update(await fs.readFile(path.join(dist, file))).digest('hex');
const version = createHash('sha256').update(JSON.stringify(sha256)).digest('hex');
await fs.writeFile(path.join(dist, 'manifest.json'), JSON.stringify({ version, files, sha256 }, null, 2) + '\n');
// Only replace this task's generated directory. Android consumes it as an assets source directory.
await fs.rm(stage, { recursive: true, force: true });
await fs.mkdir(stage, { recursive: true });
await fs.cp(dist, stage, { recursive: true });
const bytes = (await fs.stat(path.join(dist, 'main.cjs'))).size;
process.stdout.write(`LocalAPI Node18 bundle: ${files.length} assets, main.cjs ${bytes} bytes, version ${version.slice(0, 12)}\n`);
