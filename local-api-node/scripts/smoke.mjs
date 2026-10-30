// Opt-in network contract check. Public metadata only; no login, cookies, likes or mutations.
import assert from 'node:assert/strict';
import { startRuntime } from './runtime-helper.mjs';

const reference = process.env.NOCTURN_REFERENCE_API;
if (reference) {
  const parsed = new URL(reference);
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('NOCTURN_REFERENCE_API must be a plain HTTPS base URL without credentials or query.');
  }
}
const checks = [
  { route: 'search', params: { keywords: 'Nocturne', limit: 1 }, check: (body) => assert(Array.isArray(body.result?.songs), 'search result.songs array') },
  { route: 'cloudsearch', params: { keywords: 'Nocturne', limit: 1 }, check: (body) => assert(Array.isArray(body.result?.songs), 'cloudsearch result.songs array') },
  { route: 'search/suggest', params: { keywords: 'Nocturne', type: 'mobile' }, check: (body) => assert(Array.isArray(body.result?.allMatch), 'mobile suggestions array') },
  { route: 'search/default', params: {}, check: (body) => assert(typeof body.data?.showKeyword === 'string', 'default search keyword') },
  { route: 'song/detail', params: { ids: '347230' }, check: (body) => assert(body.songs?.[0]?.id === 347230, 'requested song detail') },
  { route: 'lyric', params: { id: '347230' }, check: (body) => assert(typeof body.lrc?.lyric === 'string', 'lyric string') },
  { route: 'lyric/new', params: { id: '1824020871' }, check: (body) => assert(typeof body.yrc?.lyric === 'string', 'YRC fixture string') },
  { route: 'song/music/detail', params: { id: '347230' }, check: (body) => assert(Number(body.data?.songId) === 347230, 'requested quality details') },
  { route: 'check/music', params: { id: '347230' }, check: (body) => assert(typeof body.success === 'boolean', 'availability boolean') },
  { route: 'playlist/detail', params: { id: '24381616' }, check: (body) => assert(Array.isArray(body.playlist?.trackIds), 'playlist complete track IDs') },
  { route: 'playlist/track/all', params: { id: '24381616', limit: 2, offset: 1 }, check: (body) => assert(body.songs?.length === 2, 'explicitly paginated playlist') },
  { route: 'song/url/v1', params: { id: '347230,4278232', level: 'standard' }, check: (body) => {
    assert.deepEqual(body.data?.map((entry) => entry.id).sort((a, b) => a - b), [347230, 4278232], 'both requested song URL results');
    assert(body.data.every((entry) => entry.url === null || typeof entry.url === 'string'), 'URL string or unavailable null');
  } },
];

const runtime = await startRuntime();
let failures = 0;
async function fetchPublicCheck(url, options, label) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fetch(url, { ...options, signal: AbortSignal.timeout(30000) });
    } catch (error) {
      // Every route in this script is read-only. Retry one transient transport failure only;
      // never retry assertions, business errors, login or account mutations.
      if (attempt || error.name !== 'TypeError') throw error;
      process.stdout.write(`RETRY ${label}: transport failure\n`);
    }
  }
}
try {
  for (const check of checks) {
    for (const mode of reference ? ['local', 'reference'] : ['local']) {
      // The reference uses upstream Express caching. Include a fresh timestamp for POST URLs.
      const url = mode === 'local' ? `${runtime.url}/api/${check.route}` : `${reference.replace(/\/+$/, '')}/${check.route}?timestamp=${Date.now()}`;
      try {
        const response = await fetchPublicCheck(url, {
          method: 'POST',
          headers: { 'content-type': 'application/json', ...(mode === 'local' ? { authorization: `Bearer ${runtime.token}` } : {}) },
          body: JSON.stringify(check.params),
        }, `${mode} ${check.route}`);
        const body = await response.json();
        assert.equal(response.status, 200, 'HTTP 200');
        assert.equal(body.code, 200, `business code ${body.code ?? body.error?.code ?? 'missing'}`);
        check.check(body);
        const availability = check.route === 'song/url/v1' ? `, available=${body.data.filter((entry) => entry.url).length}/${body.data.length}` : '';
        process.stdout.write(`PASS ${mode} ${check.route}${availability}\n`);
      } catch (error) {
        failures++;
        // Assertion messages are our own; avoid printing arbitrary network errors or response bodies.
        process.stdout.write(`FAIL ${mode} ${check.route}: ${error.name === 'AssertionError' ? error.message.split('\n')[0] : error.name}\n`);
      }
    }
  }
} finally { await runtime.close(); }
process.exitCode = failures ? 1 : 0;
