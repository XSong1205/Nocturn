#!/usr/bin/env node
// Downloads official nodejs-mobile binaries only. No Cordova code is embedded.
import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { access, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const buildRoot = path.join(root, 'local-api-android', 'build');
const outputRoot = path.join(buildRoot, 'node-runtime');
const cacheRoot = path.join(root, '.cache', 'node-runtime');
const version = '18.20.4';
const archiveName = `nodejs-mobile-v${version}-android.zip`;
const archiveUrl = `https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v${version}/${archiveName}`;
const archiveSha256 = 'bd7321eaa1a7602fbe0bb87302df2d79d87835cf4363fbdd17c350dbb485c2af';
const licenseUrl = `https://raw.githubusercontent.com/nodejs-mobile/nodejs-mobile/v${version}/LICENSE`;
const licenseSha256 = '66233b87054d6bca9b0c2f23b027bb2caf1ebd961a6f981616d098dade33af34';
const abis = ['arm64-v8a', 'x86_64'];

async function sha256(filename) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(filename)) hash.update(chunk);
  return hash.digest('hex');
}

function inside(parent, child) {
  const relative = path.relative(parent, child);
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`Refusing filesystem operation outside ${parent}`);
  }
}

async function removeGenerated(directory) {
  inside(buildRoot, directory);
  await rm(directory, { recursive: true, force: true });
}

async function download() {
  await mkdir(cacheRoot, { recursive: true });
  const archivePath = path.join(cacheRoot, archiveName);
  try {
    if ((await sha256(archivePath)) === archiveSha256) return archivePath;
    throw new Error('Cached runtime archive checksum mismatch; remove the corrupt archive and retry.');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  console.log(`Downloading official nodejs-mobile ${version} Android runtime…`);
  const temporary = `${archivePath}.partial-${process.pid}`;
  try {
    const response = await fetch(archiveUrl, { signal: AbortSignal.timeout(10 * 60 * 1000) });
    if (!response.ok || !response.body) throw new Error(`Runtime download returned HTTP ${response.status}`);
    await pipeline(Readable.fromWeb(response.body), createWriteStream(temporary, { flags: 'wx' }));
    if ((await sha256(temporary)) !== archiveSha256) throw new Error('Downloaded runtime SHA-256 does not match the pinned release.');
    await rename(temporary, archivePath);
    return archivePath;
  } finally {
    await rm(temporary, { force: true });
  }
}

async function license() {
  const filename = path.join(cacheRoot, `nodejs-mobile-v${version}-LICENSE`);
  try {
    const data = await readFile(filename);
    if (createHash('sha256').update(data).digest('hex') !== licenseSha256) throw new Error('Cached Node license checksum mismatch.');
    return data;
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  const response = await fetch(licenseUrl, { signal: AbortSignal.timeout(60000) });
  if (!response.ok) throw new Error(`Node license download returned HTTP ${response.status}`);
  const data = Buffer.from(await response.arrayBuffer());
  if (createHash('sha256').update(data).digest('hex') !== licenseSha256) throw new Error('Node license SHA-256 mismatch.');
  await writeFile(filename, data);
  return data;
}

// The pinned release uses ordinary ZIP records. Read only the central directory
// and allowlisted files; archive paths never become filesystem destinations.
function zipEntries(archive) {
  let eocd = -1;
  for (let offset = archive.length - 22; offset >= Math.max(0, archive.length - 65557); --offset) {
    if (archive.readUInt32LE(offset) === 0x06054b50) { eocd = offset; break; }
  }
  if (eocd < 0) throw new Error('Invalid runtime ZIP: end record missing.');
  if (archive.readUInt16LE(eocd + 4) !== 0 || archive.readUInt16LE(eocd + 6) !== 0) throw new Error('Multi-volume ZIP archives are unsupported.');
  const count = archive.readUInt16LE(eocd + 10);
  let offset = archive.readUInt32LE(eocd + 16);
  const entries = [];
  for (let index = 0; index < count; ++index) {
    if (archive.readUInt32LE(offset) !== 0x02014b50) throw new Error('Invalid runtime ZIP central directory.');
    const flags = archive.readUInt16LE(offset + 8);
    const method = archive.readUInt16LE(offset + 10);
    const compressedSize = archive.readUInt32LE(offset + 20);
    const size = archive.readUInt32LE(offset + 24);
    const nameLength = archive.readUInt16LE(offset + 28);
    const extraLength = archive.readUInt16LE(offset + 30);
    const commentLength = archive.readUInt16LE(offset + 32);
    const localOffset = archive.readUInt32LE(offset + 42);
    const name = archive.subarray(offset + 46, offset + 46 + nameLength).toString('utf8');
    if ((flags & 1) || size === 0xffffffff || compressedSize === 0xffffffff) throw new Error('Encrypted or ZIP64 runtime archives are unsupported.');
    if (name.includes('\\') || name.startsWith('/') || name.split('/').includes('..')) throw new Error('Unsafe ZIP entry.');
    entries.push({ name, method, compressedSize, size, localOffset });
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function contents(archive, entry) {
  if (archive.readUInt32LE(entry.localOffset) !== 0x04034b50) throw new Error('Invalid runtime ZIP local header.');
  const nameLength = archive.readUInt16LE(entry.localOffset + 26);
  const extraLength = archive.readUInt16LE(entry.localOffset + 28);
  const offset = entry.localOffset + 30 + nameLength + extraLength;
  const compressed = archive.subarray(offset, offset + entry.compressedSize);
  const data = entry.method === 0 ? compressed : entry.method === 8
    ? inflateRawSync(compressed, { maxOutputLength: 160 * 1024 * 1024 })
    : (() => { throw new Error(`Unsupported ZIP method ${entry.method}`); })();
  if (data.length !== entry.size) throw new Error(`Truncated runtime ZIP entry ${entry.name}`);
  return data;
}

function elfAlignment(binary) {
  if (binary.readUInt32BE(0) !== 0x7f454c46 || binary[4] !== 2 || binary[5] !== 1) throw new Error('Expected a little-endian 64-bit ELF runtime.');
  const table = Number(binary.readBigUInt64LE(32));
  const entrySize = binary.readUInt16LE(54);
  const count = binary.readUInt16LE(56);
  const alignments = [];
  for (let index = 0; index < count; ++index) {
    const offset = table + index * entrySize;
    if (binary.readUInt32LE(offset) === 1) alignments.push(Number(binary.readBigUInt64LE(offset + 48)));
  }
  if (alignments.length === 0) throw new Error('ELF has no load segments.');
  return Math.min(...alignments);
}

async function prepared() {
  try {
    const manifest = JSON.parse(await readFile(path.join(outputRoot, 'manifest.json'), 'utf8'));
    if (manifest.archiveSha256 !== archiveSha256 || manifest.licenseSha256 !== licenseSha256 || manifest.version !== version) return false;
    for (const [relative, checksum] of Object.entries(manifest.files)) {
      const filename = path.resolve(outputRoot, relative);
      inside(outputRoot, filename);
      if ((await sha256(filename)) !== checksum) return false;
    }
    await access(path.join(outputRoot, 'include', 'node', 'node.h'));
    await access(path.join(outputRoot, 'licenses', 'nodejs-mobile-LICENSE'));
    for (const abi of abis) await access(path.join(outputRoot, 'lib', abi, 'libnode.so'));
    return true;
  } catch { return false; }
}

async function main() {
  if (await prepared()) {
    console.log(`Pinned nodejs-mobile ${version} runtime is ready (cached).`);
    return;
  }
  const archivePath = await download();
  const archive = await readFile(archivePath);
  const stage = path.join(buildRoot, `node-runtime.stage-${process.pid}`);
  await mkdir(stage, { recursive: true });
  const files = {};
  const loadSegmentAlignment = {};
  try {
    for (const entry of zipEntries(archive)) {
      if (entry.name.endsWith('/')) continue;
      const header = /(?:^|\/)include\/node\/(.+)$/.exec(entry.name);
      const library = /(?:^|\/)bin\/(arm64-v8a|x86_64)\/libnode\.so$/.exec(entry.name);
      const license = /(?:^|\/)(LICENSE(?:\.txt)?|LICENSES(?:\.txt)?|NOTICE(?:\.txt)?)$/i.exec(entry.name);
      const relative = header ? path.join('include', 'node', header[1])
        : library ? path.join('lib', library[1], 'libnode.so')
        : license ? path.join('licenses', license[1]) : null;
      if (!relative) continue;
      const destination = path.resolve(stage, relative);
      inside(stage, destination);
      const data = contents(archive, entry);
      await mkdir(path.dirname(destination), { recursive: true });
      await writeFile(destination, data);
      files[relative.replaceAll(path.sep, '/')] = createHash('sha256').update(data).digest('hex');
      if (library) loadSegmentAlignment[library[1]] = elfAlignment(data);
    }
    await access(path.join(stage, 'include', 'node', 'node.h'));
    for (const abi of abis) await access(path.join(stage, 'lib', abi, 'libnode.so'));
    const licenseData = await license();
    await mkdir(path.join(stage, 'licenses'), { recursive: true });
    await writeFile(path.join(stage, 'licenses', 'nodejs-mobile-LICENSE'), licenseData);
    files['licenses/nodejs-mobile-LICENSE'] = licenseSha256;
    const manifest = { version, archiveUrl, archiveSha256, licenseUrl, licenseSha256, loadSegmentAlignment, files };
    await writeFile(path.join(stage, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
    await removeGenerated(outputRoot);
    await rename(stage, outputRoot);
    console.log(`Prepared nodejs-mobile ${version} for ${abis.join(', ')}.`);
    for (const [abi, alignment] of Object.entries(loadSegmentAlignment)) {
      if (alignment < 16384) console.warn(`${abi}: upstream libnode.so has ${alignment}-byte LOAD alignment. 16 KB devices are NOT supported by this prebuilt.`);
    }
  } finally {
    await removeGenerated(stage);
  }
}

main().catch((error) => {
  console.error(`Node runtime preparation failed: ${error.message}`);
  process.exitCode = 1;
});
