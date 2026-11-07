import assert from 'node:assert/strict';
import test from 'node:test';
import http from 'node:http';
import { startLocalApi, MAX_BODY_BYTES } from '../src/server.mjs';
import { endpoints, validateParameters } from '../src/endpoints.mjs';

const token = 'nocturn_test_' + 'b'.repeat(48);
const authorization = `Bearer ${token}`;
async function fixture(t, provider = async (_, params) => ({ body: { code: 200, echoed: params } }), options = {}) {
  const api = await startLocalApi({ token, provider, ...options });
  t.after(() => api.close());
  return { ...api, url: `http://127.0.0.1:${api.port}` };
}
function post(api, route, body, headers = {}) {
  return fetch(`${api.url}/api/${route}`, { method: 'POST', headers: { authorization, 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
}

test('health is unauthenticated, loopback-only and discloses no credentials', async (t) => {
  const api = await fixture(t);
  assert.equal(api.server.address().address, '127.0.0.1');
  const response = await fetch(`${api.url}/health`);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { app: 'Nocturn LocalAPI', version: '0.1.0', status: 'ready' });
});

test('token strength and valid port are mandatory before binding', async () => {
  const provider = async () => ({});
  for (const invalid of [undefined, '', 'short', 'a'.repeat(300), ' '.repeat(40)]) {
    await assert.rejects(startLocalApi({ token: invalid, provider }), /TOKEN/);
  }
  for (const port of [-1, 65536, 1.5, NaN]) await assert.rejects(startLocalApi({ token, port, provider }), /port/);
});

test('listen errors reject startup rather than publishing readiness', async (t) => {
  const api = await fixture(t);
  await assert.rejects(startLocalApi({ token, port: api.port, provider: async () => ({}) }), { code: 'EADDRINUSE' });
});

test('missing and incorrect auth never invoke the provider', async (t) => {
  let calls = 0;
  const api = await fixture(t, async () => { calls++; return { body: {} }; });
  for (const auth of ['', 'Basic secret', `Bearer ${'x'.repeat(60)}`]) {
    const response = await post(api, 'search', { keywords: 'Nocturn' }, { authorization: auth });
    assert.equal(response.status, 401);
    assert.equal((await response.json()).error.code, 'UNAUTHORIZED');
  }
  assert.equal(calls, 0);
});

test('browser origins, wrong methods and arbitrary module routes are rejected', async (t) => {
  const api = await fixture(t);
  assert.equal((await post(api, 'search', { keywords: 'test' }, { origin: 'https://example.com' })).status, 403);
  assert.equal((await fetch(`${api.url}/api/search`, { headers: { authorization } })).status, 405);
  assert.equal((await fetch(`${api.url}/health`, { method: 'POST' })).status, 405);
  for (const route of ['api', 'server', 'constructor', '__proto__', 'song/url/match']) {
    assert.equal((await post(api, route, {})).status, 404);
  }
  assert.equal((await fetch(`${api.url}/api/search?cookie=secret`, { headers: { authorization } })).status, 404);
});

test('successful body, cookie and QR business code are preserved', async (t) => {
  const expected = { code: 803, message: 'confirmed', cookie: 'MUSIC_U=secret;', data: { userId: 123 } };
  const api = await fixture(t, async (endpoint, params) => {
    assert.equal(endpoint, 'login/qr/check');
    assert.equal(params.cookie, 'MUSIC_U=input;');
    return { status: 200, body: expected };
  });
  const response = await post(api, 'login/qr/check', { key: 'public_test_key', cookie: 'MUSIC_U=input;' });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('set-cookie'), null);
  assert.deepEqual(await response.json(), expected);
});

test('malformed JSON, wrong content types, compressed and oversized bodies are rejected', async (t) => {
  const api = await fixture(t);
  const malformed = await fetch(`${api.url}/api/search`, { method: 'POST', headers: { authorization, 'content-type': 'application/json' }, body: '{' });
  assert.equal(malformed.status, 400);
  assert.equal((await malformed.json()).error.code, 'INVALID_JSON');
  assert.equal((await post(api, 'search', {}, { 'content-type': 'text/plain' })).status, 415);
  assert.equal((await post(api, 'search', {}, { 'content-encoding': 'gzip' })).status, 415);
  assert.equal((await post(api, 'search', { keywords: 'x'.repeat(MAX_BODY_BYTES) })).status, 413);
});

test('streaming bodies cannot bypass the size cap using chunked encoding', async (t) => {
  const api = await fixture(t);
  const status = await new Promise((resolve, reject) => {
    const req = http.request(`${api.url}/api/search`, { method: 'POST', headers: { authorization, 'content-type': 'application/json' } }, (res) => { res.resume(); resolve(res.statusCode); });
    req.on('error', reject);
    req.write('{"keywords":"');
    req.write('x'.repeat(MAX_BODY_BYTES + 1));
    req.end('"}');
  });
  assert.equal(status, 413);
});

test('parameter validation prevents transport override, dangerous keys and invalid pagination', async (t) => {
  const api = await fixture(t);
  for (const field of ['proxy', 'domain', 'crypto', 'headers', 'timeout', 'unblock', 'source', '__proto__']) {
    const response = await post(api, 'search', JSON.parse(`{"keywords":"test","${field}":"value"}`));
    assert.equal(response.status, 400, field);
  }
  for (const body of [[], null, { keywords: '' }, { keywords: 'x', limit: 201 }, { keywords: 'x', offset: -1 }, { keywords: 'x', cookie: { MUSIC_U: 'a' } }, { keywords: 'x', cookie: 'a\nb' }]) {
    assert.equal((await post(api, 'search', body)).status, 400);
  }
  assert.equal((await post(api, 'song/url/v1', { id: '123', level: 'lossless' })).status, 200);
  assert.equal((await post(api, 'song/detail', { ids: '123,456' })).status, 200);
  assert.equal((await post(api, 'song/detail', { ids: 123 })).status, 400);
  assert.equal((await post(api, 'song/url/v1', { id: '123', level: 'invalid' })).status, 400);
});

test('upstream errors are normalized without leaking arbitrary error text', async (t) => {
  const api = await fixture(t, async () => { throw new Error('MUSIC_U=secret /data/private'); });
  const response = await post(api, 'search', { keywords: 'test' });
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: { code: 'UPSTREAM_FAILURE', message: 'Music service request failed.' } });
});

test('upstream transport errors with body are normalized, business denial preserved', async (t) => {
  const api = await fixture(t, async (_, body) => {
    if (body.keywords === 'network') throw { status: 502, body: { code: 502, msg: 'secret URL' } };
    throw { status: 301, body: { code: 301, msg: 'Login required.' } };
  });
  const failed = await post(api, 'search', { keywords: 'network' });
  assert.equal(failed.status, 502);
  assert.equal((await failed.json()).error.code, 'UPSTREAM_FAILURE');
  const denied = await post(api, 'search', { keywords: 'business' });
  assert.equal(denied.status, 200);
  assert.deepEqual(await denied.json(), { code: 301, msg: 'Login required.' });
});

test('request timeout responds promptly and capacity recovers', async (t) => {
  let calls = 0;
  const api = await fixture(t, async () => {
    calls++;
    if (calls === 1) return new Promise(() => {});
    return { body: { code: 200 } };
  }, { requestTimeoutMs: 30 });
  const timed = await post(api, 'search', { keywords: 'test' });
  assert.equal(timed.status, 504);
  assert.equal((await timed.json()).error.code, 'UPSTREAM_TIMEOUT');
  assert.equal((await post(api, 'search', { keywords: 'test' })).status, 200);
});

test('concurrent request budget returns BUSY without invoking another provider', async (t) => {
  let release;
  let entered;
  const started = new Promise((resolve) => { entered = resolve; });
  const wait = new Promise((resolve) => { release = resolve; });
  const api = await fixture(t, async () => { entered(); await wait; return { body: { code: 200 } }; }, { maxConcurrent: 1 });
  const first = post(api, 'search', { keywords: 'first' });
  await started;
  const second = await post(api, 'search', { keywords: 'second' });
  assert.equal(second.status, 429);
  release();
  assert.equal((await first).status, 200);
});

test('all endpoint specs reject inherited object properties', () => {
  assert.equal(validateParameters('search', { keywords: 'music', limit: 20 }), null);
  assert.match(validateParameters('search', { keywords: 'music', constructor: 'bad' }), /Unsupported/);
  assert.match(validateParameters('constructor', {}), /Unknown/);
});

test('batch song URLs preserve every ID, quality and account credential', async (t) => {
  const expected = { id: '347230,4278232', level: 'dolby', cookie: 'MUSIC_U=test;' };
  const api = await fixture(t, async (route, params) => {
    assert.equal(route, 'song/url/v1');
    assert.deepEqual(params, expected);
    return { body: { code: 200, data: [{ id: 347230, url: null }, { id: 4278232, url: 'https://example.test/audio' }] } };
  });
  const response = await post(api, 'song/url/v1', expected);
  assert.equal(response.status, 200);
  assert.equal((await response.json()).data.length, 2);
});

test('ID lists are bounded, positive and do not silently lose numeric precision', () => {
  for (const id of ['', '0', '1,,2', '1,-2', '1,0', Array(201).fill('1').join(','), Number.MAX_SAFE_INTEGER + 1]) {
    assert(validateParameters('song/url/v1', { id, level: 'standard' }), String(id).slice(0, 30));
  }
  assert.equal(validateParameters('song/url/v1', { id: '9223372036854775807,347230', level: 'hires' }), null);
  assert(validateParameters('lyric', { id: '1,2' }));
  assert(validateParameters('song/url/v1', { id: '1', level: 'vivid' }));
  assert.equal(validateParameters('like', { id: '1' }), null);
});

test('search, suggestions and banner validate their own type namespaces', async (t) => {
  const api = await fixture(t);
  for (const [route, params] of [
    ['cloudsearch', { keywords: '夜曲', type: 1018, offset: 30, limit: 30 }],
    ['search', { keywords: '夜曲', type: 1018 }],
    ['search/suggest', { keywords: '夜曲', type: 'mobile' }],
    ['banner', { type: 1 }],
    ['recommend/songs', { afresh: false }],
    ['homepage/block/page', { refresh: false, cursor: 'opaque_cursor' }],
  ]) assert.equal((await post(api, route, params)).status, 200, route);
  for (const [route, params] of [
    ['search/suggest', { keywords: '夜曲', type: 1 }],
    ['cloudsearch', { keywords: '夜曲', type: 'mobile' }],
    ['banner', { type: 1018 }],
    ['recommend/songs', { afresh: 'false' }],
    ['homepage/block/page', { refresh: 'false' }],
  ]) assert.equal((await post(api, route, params)).status, 400, route);
});

test('account endpoints dispatch explicit actions with one phone credential', async (t) => {
  const api = await fixture(t);
  for (const [route, params] of [
    ['register/anonimous', {}], ['login/refresh', { cookie: 'test' }],
    ['user/account', { cookie: 'test' }], ['user/subcount', { cookie: 'test' }],
    ['captcha/sent', { phone: '13800138000', ctcode: '86' }],
    ['captcha/verify', { phone: '13800138000', captcha: '012345', ctcode: '86' }],
    ['login/cellphone', { phone: '13800138000', captcha: '012345', countrycode: '86' }],
    ['login/cellphone', { phone: '13800138000', password: 'local-secret', countrycode: '86' }],
    ['song/like', { id: '1', uid: '2', like: false }],
    ['user/playlist/create', { uid: '2', limit: 50, offset: 50 }],
    ['user/playlist/collect', { uid: '2' }], ['record/recent/song', { limit: 20 }],
  ]) assert.equal((await post(api, route, params)).status, 200, route);
  for (const params of [
    { phone: 'not-a-phone', captcha: '1234' },
    { phone: '13800138000', captcha: 'not-a-code' },
    { phone: '13800138000' },
    { phone: '13800138000', password: '' },
    { phone: '13800138000', password: 'secret', captcha: '1234' },
    { phone: '13800138000', md5_password: 'secret' },
    { phone: '13800138000', captcha: '1234', countrycode: '0' },
  ]) assert.equal((await post(api, 'login/cellphone', params)).status, 400);
});
