import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import { endpoints } from '../src/endpoints.mjs';
const require = createRequire(import.meta.url);
const parameters = require('../src/provider-parameters.cjs');

test('Kotlin and Node expose the same endpoint contract', () => {
  const source = fs.readFileSync(new URL('../../local-api/src/commonMain/kotlin/dev/nocturn/localapi/client/LocalApiEndpoint.kt', import.meta.url), 'utf8');
  const kotlinPaths = [...source.matchAll(/\("([a-z0-9/]+)"\)/g)].map((match) => match[1]);
  assert.deepEqual(kotlinPaths.sort(), Object.keys(endpoints).sort());
});

test('JSON false really cancels likes through both upstream modules', async () => {
  for (const name of ['like', 'song_like']) {
    const handler = require(`@neteasecloudmusicapienhanced/api/module/${name}.js`);
    for (const like of [true, false, 'true', 'false']) {
      const original = { id: '347230', uid: '123', like };
      const query = parameters(original, {});
      const result = await handler(query, async (_url, body) => body);
      assert.equal(result.like, String(like) === 'true', `${name}: ${like}`);
      assert.equal(original.like, like, 'caller parameters must not be mutated');
    }
  }
});

test('non-like booleans preserve their meaning in the upstream handlers', async () => {
  const recommend = require('@neteasecloudmusicapienhanced/api/module/recommend_songs.js');
  const homepage = require('@neteasecloudmusicapienhanced/api/module/homepage_block_page.js');
  for (const flag of [true, false]) {
    const daily = await recommend(parameters({ afresh: flag }, {}), async (_url, body) => body);
    const home = await homepage(parameters({ refresh: flag, cursor: 'next' }, {}), async (_url, body) => body);
    assert.equal(daily.afresh, flag);
    assert.equal(home.refresh, flag);
    assert.equal(home.cursor, 'next');
  }
});
