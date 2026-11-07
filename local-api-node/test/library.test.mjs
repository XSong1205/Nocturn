import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
import { startLocalApi } from '../src/server.mjs';
import { validateParameters } from '../src/endpoints.mjs';

const require = createRequire(import.meta.url);
const parameters = require('../src/provider-parameters.cjs');
const playlistTracks = require('../src/playlist-tracks.cjs');
const cookie = 'MUSIC_U=isolated-test;';
const token = 'nocturn_library_' + 'b'.repeat(48);
const validRequests = [
  ['playlist/catlist', {}],
  ['playlist/hot', {}],
  ['playlist/highquality/tags', {}],
  ['top/playlist', { cat: '华语', order: 'new', limit: 30, offset: 30 }],
  ['top/playlist/highquality', { cat: '全部', limit: 30, before: '1793000000000' }],
  ['playlist/create', { name: '夜曲', privacy: 10, type: 'NORMAL', cookie }],
  ['playlist/delete', { id: '9007199254740993,9007199254740995', cookie }],
  ['playlist/tracks', { pid: '9007199254740993', tracks: '347230,4278232', op: 'add', cookie }],
  ['playlist/name/update', { id: '123', name: '夜曲 "选集"', cookie }],
  ['playlist/desc/update', { id: '123', desc: '', cookie }],
  ['playlist/tags/update', { id: '123', tags: '', cookie }],
  ['album/sub', { id: '123', t: 2, cookie }],
  ['album/sublist', { limit: 30, offset: 30, cookie }],
  ['artist/sub', { id: '123', t: 1, cookie }],
  ['artist/sublist', { limit: 30, offset: 30, cookie }],
  ['artist/songs', { id: '123', order: 'time', limit: 30, offset: 30 }],
  ['personalized/newsong', { limit: 30 }],
  ['recommend/resource', { cookie }],
];

async function fixture(t, provider) {
  const api = await startLocalApi({ token, provider });
  t.after(() => api.close());
  return {
    post: (route, body) => fetch(`http://127.0.0.1:${api.port}/api/${route}`, {
      method: 'POST',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      body: JSON.stringify(body),
    }),
  };
}

test('discovery and library routes preserve explicit fields through authenticated POST', async (t) => {
  let calls = 0;
  const api = await fixture(t, async (route, params) => {
    const [expectedRoute, expectedParams] = validRequests[calls++];
    assert.equal(route, expectedRoute);
    assert.deepEqual(params, expectedParams);
    return { status: 200, body: { code: 200 } };
  });
  for (const [route, params] of validRequests) {
    const response = await api.post(route, params);
    assert.equal(response.status, 200, route);
    assert.deepEqual(await response.json(), { code: 200 });
  }
  assert.equal(calls, validRequests.length);
});

test('library mutations and private collection pages require a nonblank cookie before dispatch', async (t) => {
  let calls = 0;
  const api = await fixture(t, async () => { calls++; return { body: { code: 200 } }; });
  for (const [route, original] of validRequests.filter(([, params]) => Object.hasOwn(params, 'cookie'))) {
    const { cookie: _cookie, ...params } = original;
    for (const invalid of [params, { ...params, cookie: '' }, { ...params, cookie: '   ' }]) {
      assert.equal((await api.post(route, invalid)).status, 400, route);
    }
  }
  assert.equal(calls, 0);
});

test('new routes reject transport controls and unsupported pagination before dispatch', async (t) => {
  let calls = 0;
  const api = await fixture(t, async () => { calls++; return { body: { code: 200 } }; });
  for (const [route, params] of validRequests) {
    for (const key of ['domain', 'crypto', 'headers', 'proxy', 'timeout']) {
      assert.equal((await api.post(route, { ...params, [key]: 'override' })).status, 400, `${route}: ${key}`);
    }
  }
  assert.equal((await api.post('top/playlist/highquality', { offset: 30 })).status, 400);
  assert.equal((await api.post('artist/songs', { id: '123', limit: 201 })).status, 400);
  assert.equal((await api.post('album/sublist', { cookie, offset: 100001 })).status, 400);
  assert.equal(calls, 0);
});

test('discovery and artist sorts have distinct namespaces and safe cursor bounds', () => {
  for (const order of ['hot', 'new']) assert.equal(validateParameters('top/playlist', { order }), null);
  for (const order of ['hot', 'time']) assert.equal(validateParameters('artist/songs', { id: '1', order }), null);
  assert.match(validateParameters('top/playlist', { order: 'time' }), /order/);
  assert.match(validateParameters('artist/songs', { id: '1', order: 'new' }), /order/);
  for (const before of [0, '0', Number.MAX_SAFE_INTEGER, String(Number.MAX_SAFE_INTEGER)]) {
    assert.equal(validateParameters('top/playlist/highquality', { before }), null);
  }
  for (const before of [-1, 1.5, '1e3', true, Number.MAX_SAFE_INTEGER + 1, '9007199254740992']) {
    assert.match(validateParameters('top/playlist/highquality', { before }), /before/);
  }
  for (const cat of ['', ' ', 'a'.repeat(41), 1]) assert.match(validateParameters('top/playlist', { cat }), /cat/);
  assert.equal(validateParameters('top/playlist', { cat: 'a'.repeat(40), limit: 200, offset: 100000 }), null);
});

test('personalized new songs accept only a bounded limit and reach the upstream recommend list', async () => {
  for (const body of [{}, { limit: 1 }, { limit: '200' }, { limit: 30, cookie }]) {
    assert.equal(validateParameters('personalized/newsong', body), null);
  }
  for (const limit of [0, 201, -1, 1.5, '1e3', true]) {
    assert.match(validateParameters('personalized/newsong', { limit }), /limit/);
  }
  for (const key of ['offset', 'areaId', 'type']) {
    assert.match(validateParameters('personalized/newsong', { [key]: 1 }), /Unsupported/);
  }
  const handler = require('@neteasecloudmusicapienhanced/api/module/personalized_newsong.js');
  let calls = 0;
  await handler(parameters({ limit: 30 }, { MUSIC_U: 'test' }), async (url, data, options) => {
    calls++;
    assert.equal(url, '/api/personalized/newsong');
    assert.deepEqual(data, { type: 'recommend', limit: 30, areaId: 0 });
    // The account cookie must survive into the upstream request, otherwise "根据你喜爱的歌曲推荐"
    // silently degrades to the anonymous list.
    assert.deepEqual(options.cookie, { MUSIC_U: 'test' });
    return { status: 200, body: { code: 200, result: [] } };
  });
  assert.equal(calls, 1);
});

test('account playlist recommendations require a cookie and reach the daily resource list', async () => {
  for (const body of [{}, { cookie: '' }, { cookie: '   ' }]) {
    assert.match(validateParameters('recommend/resource', body), /cookie/);
  }
  assert.equal(validateParameters('recommend/resource', { cookie }), null);
  for (const key of ['limit', 'offset']) {
    assert.match(validateParameters('recommend/resource', { cookie, [key]: 10 }), /Unsupported/);
  }
  const handler = require('@neteasecloudmusicapienhanced/api/module/recommend_resource.js');
  let calls = 0;
  await handler(parameters({ cookie }, { MUSIC_U: 'isolated-test' }), async (url, _data, options) => {
    calls++;
    assert.equal(url, '/api/v1/discovery/recommend/resource');
    assert.deepEqual(options.cookie, { MUSIC_U: 'isolated-test' });
    return { status: 200, body: { code: 200, recommend: [] } };
  });
  assert.equal(calls, 1);
});

test('playlist metadata validates bounds and permits explicitly clearing description and tags', () => {
  for (const privacy of [0, '0', 10, '10']) {
    for (const type of ['NORMAL', 'VIDEO', 'SHARED']) {
      assert.equal(validateParameters('playlist/create', { name: 'a'.repeat(40), privacy, type, cookie }), null);
    }
  }
  for (const name of ['', ' ', 'a'.repeat(41), 1]) {
    assert.match(validateParameters('playlist/create', { name, cookie }), /name/);
    assert.match(validateParameters('playlist/name/update', { id: '1', name, cookie }), /name/);
  }
  assert.match(validateParameters('playlist/create', { name: 'valid', privacy: 1, cookie }), /privacy/);
  assert.match(validateParameters('playlist/create', { name: 'valid', type: 1, cookie }), /type/);
  for (const desc of ['', 'a'.repeat(1000)]) {
    assert.equal(validateParameters('playlist/desc/update', { id: '1', desc, cookie }), null);
  }
  assert.match(validateParameters('playlist/desc/update', { id: '1', cookie }), /desc/);
  assert.match(validateParameters('playlist/desc/update', { id: '1', desc: 'a'.repeat(1001), cookie }), /desc/);
  for (const tags of ['', '古典', '古典;钢琴;夜晚', 'a'.repeat(40)]) {
    assert.equal(validateParameters('playlist/tags/update', { id: '1', tags, cookie }), null);
  }
  for (const tags of [' ', 'a;', ';a', 'a;;b', 'a;b;c;d', 'a'.repeat(41), 1]) {
    assert.match(validateParameters('playlist/tags/update', { id: '1', tags, cookie }), /tags/);
  }
  assert.match(validateParameters('playlist/tags/update', { id: '1', cookie }), /tags/);
});

test('playlist edits bound song batches and keep playlist IDs scalar except batch deletion', () => {
  const maxBatch = Array.from({ length: 200 }, () => '99999999999999999999').join(',');
  assert.equal(validateParameters('playlist/tracks', { pid: '123', tracks: maxBatch, op: 'del', cookie }), null);
  assert.equal(validateParameters('playlist/delete', { id: maxBatch, cookie }), null);
  for (const tracks of ['', '1,', '1,0', '1,-2', '1, 2', Array(201).fill('1').join(','), 1]) {
    assert.match(validateParameters('playlist/tracks', { pid: '123', tracks, op: 'add', cookie }), /tracks/);
  }
  assert.match(validateParameters('playlist/tracks', { pid: '1,2', tracks: '1', op: 'add', cookie }), /pid/);
  assert.match(validateParameters('playlist/tracks', { pid: '123', tracks: '1', op: 'remove', cookie }), /op/);
  assert.match(validateParameters('playlist/delete', { id: Array(201).fill('1').join(','), cookie }), /id/);
  assert.match(validateParameters('playlist/name/update', { id: '1,2', name: 'name', cookie }), /id/);
});

test('upstream metadata modules preserve quotes, newlines, Unicode and clear values', async () => {
  const changes = [
    ['playlist_create', 'name', '夜曲 "精选"\\合集\n第二辑'],
    ['playlist_name_update', 'name', '夜曲 "精选"\\合集\n第二辑'],
    ['playlist_desc_update', 'desc', '说明 "引用"\\路径\n下一行'],
    ['playlist_desc_update', 'desc', ''],
    ['playlist_tags_update', 'tags', '古典;钢琴'],
    ['playlist_tags_update', 'tags', ''],
  ];
  for (const [name, field, value] of changes) {
    const handler = require(`@neteasecloudmusicapienhanced/api/module/${name}.js`);
    let calls = 0;
    const result = await handler(parameters({ id: '9007199254740993', [field]: value }, { MUSIC_U: 'test' }), async (_url, data) => {
      calls++;
      assert.equal(data[field], value);
      return { status: 200, body: { code: 200 } };
    });
    assert.equal(calls, 1);
    assert.equal(result.body.code, 200);
  }
});

test('playlist song edits submit once and return the original success envelope', async () => {
  for (const op of ['add', 'del']) {
    const original = { pid: '9007199254740993', tracks: '9007199254740995,347230', op };
    const expected = { status: 200, body: { code: 200, count: 2 }, cookie: [] };
    let calls = 0;
    const result = await playlistTracks(parameters(original, { MUSIC_U: 'test' }), async (url, data, options) => {
      calls++;
      assert.equal(url, '/api/playlist/manipulate/tracks');
      assert.deepEqual(data, { op, pid: '9007199254740993', trackIds: '["9007199254740995","347230"]', imme: 'true' });
      assert.equal(options.timeout, 12000);
      assert.deepEqual(options.cookie, { MUSIC_U: 'test' });
      return expected;
    });
    assert.equal(calls, 1);
    assert.equal(result, expected);
    assert.deepEqual(original, { pid: '9007199254740993', tracks: '9007199254740995,347230', op });
  }
});

test('playlist code 512 is returned unchanged after one submission and other failures propagate', async () => {
  const query = parameters({ pid: '123', tracks: '347230', op: 'add' }, { MUSIC_U: 'test' });
  const duplicate = { code: 512, message: 'Already present.' };
  let calls = 0;
  const result = await playlistTracks(query, async () => { calls++; throw { status: 512, body: duplicate }; });
  assert.equal(calls, 1);
  assert.deepEqual(result, { status: 200, body: duplicate });
  assert.equal(result.body, duplicate);
  for (const error of [new Error('transport failure'), { status: 301, body: { code: 301 } }, { status: 502, body: { code: 502 } }]) {
    calls = 0;
    await assert.rejects(playlistTracks(query, async () => { calls++; throw error; }), (actual) => actual === error);
    assert.equal(calls, 1);
  }
});

test('playlist edits expose flat business results through the local HTTP server', async (t) => {
  let calls = 0;
  const api = await fixture(t, async (_route, body) => playlistTracks(parameters(body, {}), async () => {
    calls++;
    if (body.op === 'del') throw { status: 512, body: { code: 512, message: 'Unavailable.' } };
    return { status: 200, body: { code: 200, count: 1 } };
  }));
  const added = await api.post('playlist/tracks', { pid: '123', tracks: '347230', op: 'add', cookie });
  assert.equal(added.status, 200);
  assert.deepEqual(await added.json(), { code: 200, count: 1 });
  const removed = await api.post('playlist/tracks', { pid: '123', tracks: '347230', op: 'del', cookie });
  assert.equal(removed.status, 200);
  assert.deepEqual(await removed.json(), { code: 512, message: 'Unavailable.' });
  assert.equal(calls, 2);
});

test('collection modules map subscribe and unsubscribe to the corresponding actions', async () => {
  for (const kind of ['album', 'artist']) {
    const handler = require(`@neteasecloudmusicapienhanced/api/module/${kind}_sub.js`);
    for (const t of [1, 2, '1', '2']) {
      assert.equal(validateParameters(`${kind}/sub`, { id: '123', t, cookie }), null);
      let calls = 0;
      await handler(parameters({ id: '9007199254740993', t }, {}), async (url, data) => {
        calls++;
        assert.equal(url, `/api/${kind}/${String(t) === '1' ? 'sub' : 'unsub'}`);
        assert.equal(data[kind === 'artist' ? 'artistId' : 'id'], '9007199254740993');
        return { status: 200, body: { code: 200 } };
      });
      assert.equal(calls, 1);
    }
    for (const t of [0, 3, true, 'subscribe']) assert.match(validateParameters(`${kind}/sub`, { id: '123', t, cookie }), /t/);
  }
});

test('upstream discovery and collection modules receive the correct page or time cursor', async () => {
  const cases = [
    ['top_playlist', { cat: '钢琴', order: 'new', limit: 20, offset: 40 }, { cat: '钢琴', order: 'new', limit: 20, offset: 40 }],
    ['top_playlist_highquality', { cat: '钢琴', before: '1793000000000', limit: 20 }, { cat: '钢琴', lasttime: '1793000000000', limit: 20 }],
    ['artist_songs', { id: '123', order: 'time', limit: 20, offset: 40 }, { id: '123', order: 'time', limit: 20, offset: 40 }],
    ['album_sublist', { limit: 20, offset: 40 }, { limit: 20, offset: 40 }],
    ['artist_sublist', { limit: 20, offset: 40 }, { limit: 20, offset: 40 }],
  ];
  for (const [name, query, expected] of cases) {
    const handler = require(`@neteasecloudmusicapienhanced/api/module/${name}.js`);
    await handler(parameters(query, { MUSIC_U: 'test' }), async (_url, data) => {
      for (const [key, value] of Object.entries(expected)) assert.equal(data[key], value, `${name}: ${key}`);
      assert.equal(Object.hasOwn(data, name === 'top_playlist_highquality' ? 'offset' : 'lasttime'), false);
      return { status: 200, body: { code: 200 } };
    });
  }
});
