// Independent adapter around explicitly selected upstream API modules. No upstream HTTP server is loaded.
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const providerParameters = require('./provider-parameters.cjs');

module.exports = function createProvider(dataDirectory) {
  fs.mkdirSync(dataDirectory, { recursive: true });
  // Upstream uses os.tmpdir() for key caches. Set it before loading any upstream module.
  process.env.TMPDIR = dataDirectory;
  process.env.TMP = dataDirectory;
  process.env.TEMP = dataDirectory;
  const anonymousPath = path.join(dataDirectory, 'anonymous_token');
  if (!fs.existsSync(anonymousPath)) fs.writeFileSync(anonymousPath, '', { mode: 0o600 });
  const devicePath = path.join(dataDirectory, 'device-id');
  if (!fs.existsSync(devicePath)) fs.writeFileSync(devicePath, randomBytes(26).toString('hex').toUpperCase(), { mode: 0o600 });
  global.deviceId = fs.readFileSync(devicePath, 'utf8').trim();
  const modules = {
  'login/qr/key': require('@neteasecloudmusicapienhanced/api/module/login_qr_key.js'),
  'login/qr/create': require('@neteasecloudmusicapienhanced/api/module/login_qr_create.js'),
  'login/qr/check': require('@neteasecloudmusicapienhanced/api/module/login_qr_check.js'),
  'login/status': require('@neteasecloudmusicapienhanced/api/module/login_status.js'),
  'login/refresh': require('@neteasecloudmusicapienhanced/api/module/login_refresh.js'),
  'register/anonimous': require('@neteasecloudmusicapienhanced/api/module/register_anonimous.js'),
  'captcha/sent': require('@neteasecloudmusicapienhanced/api/module/captcha_sent.js'),
  'captcha/verify': require('@neteasecloudmusicapienhanced/api/module/captcha_verify.js'),
  'login/cellphone': require('@neteasecloudmusicapienhanced/api/module/login_cellphone.js'),
  logout: require('@neteasecloudmusicapienhanced/api/module/logout.js'),
  search: require('@neteasecloudmusicapienhanced/api/module/search.js'),
  cloudsearch: require('@neteasecloudmusicapienhanced/api/module/cloudsearch.js'),
  'search/suggest': require('@neteasecloudmusicapienhanced/api/module/search_suggest.js'),
  'search/default': require('@neteasecloudmusicapienhanced/api/module/search_default.js'),
  'search/hot/detail': require('@neteasecloudmusicapienhanced/api/module/search_hot_detail.js'),
  'song/detail': require('@neteasecloudmusicapienhanced/api/module/song_detail.js'),
  'song/url/v1': require('@neteasecloudmusicapienhanced/api/module/song_url_v1.js'),
  'check/music': require('@neteasecloudmusicapienhanced/api/module/check_music.js'),
  'song/music/detail': require('@neteasecloudmusicapienhanced/api/module/song_music_detail.js'),
  lyric: require('@neteasecloudmusicapienhanced/api/module/lyric.js'),
  'lyric/new': require('@neteasecloudmusicapienhanced/api/module/lyric_new.js'),
  personalized: require('@neteasecloudmusicapienhanced/api/module/personalized.js'),
  'personalized/newsong': require('@neteasecloudmusicapienhanced/api/module/personalized_newsong.js'),
  'recommend/resource': require('@neteasecloudmusicapienhanced/api/module/recommend_resource.js'),
  'recommend/songs': require('@neteasecloudmusicapienhanced/api/module/recommend_songs.js'),
  'homepage/block/page': require('@neteasecloudmusicapienhanced/api/module/homepage_block_page.js'),
  banner: require('@neteasecloudmusicapienhanced/api/module/banner.js'),
  toplist: require('@neteasecloudmusicapienhanced/api/module/toplist.js'),
  'toplist/detail': require('@neteasecloudmusicapienhanced/api/module/toplist_detail.js'),
  'playlist/catlist': require('@neteasecloudmusicapienhanced/api/module/playlist_catlist.js'),
  'playlist/hot': require('@neteasecloudmusicapienhanced/api/module/playlist_hot.js'),
  'playlist/highquality/tags': require('@neteasecloudmusicapienhanced/api/module/playlist_highquality_tags.js'),
  'top/playlist': require('@neteasecloudmusicapienhanced/api/module/top_playlist.js'),
  'top/playlist/highquality': require('@neteasecloudmusicapienhanced/api/module/top_playlist_highquality.js'),
  'playlist/detail': require('@neteasecloudmusicapienhanced/api/module/playlist_detail.js'),
  'playlist/track/all': require('@neteasecloudmusicapienhanced/api/module/playlist_track_all.js'),
  'playlist/subscribe': require('@neteasecloudmusicapienhanced/api/module/playlist_subscribe.js'),
  'playlist/create': require('@neteasecloudmusicapienhanced/api/module/playlist_create.js'),
  'playlist/delete': require('@neteasecloudmusicapienhanced/api/module/playlist_delete.js'),
  'playlist/tracks': require('./playlist-tracks.cjs'),
  'playlist/name/update': require('@neteasecloudmusicapienhanced/api/module/playlist_name_update.js'),
  'playlist/desc/update': require('@neteasecloudmusicapienhanced/api/module/playlist_desc_update.js'),
  'playlist/tags/update': require('@neteasecloudmusicapienhanced/api/module/playlist_tags_update.js'),
  'user/detail': require('@neteasecloudmusicapienhanced/api/module/user_detail.js'),
  'user/account': require('@neteasecloudmusicapienhanced/api/module/user_account.js'),
  'user/subcount': require('@neteasecloudmusicapienhanced/api/module/user_subcount.js'),
  'user/playlist': require('@neteasecloudmusicapienhanced/api/module/user_playlist.js'),
  'user/playlist/create': require('@neteasecloudmusicapienhanced/api/module/user_playlist_create.js'),
  'user/playlist/collect': require('@neteasecloudmusicapienhanced/api/module/user_playlist_collect.js'),
  'record/recent/song': require('@neteasecloudmusicapienhanced/api/module/record_recent_song.js'),
  likelist: require('@neteasecloudmusicapienhanced/api/module/likelist.js'),
  like: require('@neteasecloudmusicapienhanced/api/module/like.js'),
  'song/like': require('@neteasecloudmusicapienhanced/api/module/song_like.js'),
  album: require('@neteasecloudmusicapienhanced/api/module/album.js'),
  'album/sub': require('@neteasecloudmusicapienhanced/api/module/album_sub.js'),
  'album/sublist': require('@neteasecloudmusicapienhanced/api/module/album_sublist.js'),
  artists: require('@neteasecloudmusicapienhanced/api/module/artists.js'),
  'artist/detail': require('@neteasecloudmusicapienhanced/api/module/artist_detail.js'),
  'artist/album': require('@neteasecloudmusicapienhanced/api/module/artist_album.js'),
  'artist/songs': require('@neteasecloudmusicapienhanced/api/module/artist_songs.js'),
  'artist/sub': require('@neteasecloudmusicapienhanced/api/module/artist_sub.js'),
  'artist/sublist': require('@neteasecloudmusicapienhanced/api/module/artist_sublist.js'),
  'personal/fm': require('@neteasecloudmusicapienhanced/api/module/personal_fm.js'),
  };
  const axios = require('axios').default;
  axios.defaults.timeout = 12000;
  const { cookieToJson } = require('@neteasecloudmusicapienhanced/api/util/index.js');
  const request = require('@neteasecloudmusicapienhanced/api/util/request.js');
  const registerXeapi = require('@neteasecloudmusicapienhanced/api/module/register_xeapikey.js');
  let keyPromise;
  let keyDeviceId;
  async function ensureXeapiKey() {
    if (keyDeviceId !== global.deviceId) { keyPromise = undefined; keyDeviceId = global.deviceId; }
    if (!keyPromise) {
      keyPromise = (async () => {
        const keyPath = path.join(dataDirectory, 'xeapi_public_key');
        let current = {};
        try { current = JSON.parse(fs.readFileSync(keyPath, 'utf8')); } catch { /* cold start */ }
        if (current.sk && current.deviceId === global.deviceId) return;
        const result = await registerXeapi({ deviceId: global.deviceId }, null);
        if (!result?.body?.sk) throw new Error('Missing upstream key.');
        fs.writeFileSync(keyPath, JSON.stringify(result.body), { mode: 0o600 });
      })().catch((error) => { keyPromise = undefined; throw error; });
    }
    return keyPromise;
  }
  return async function provider(endpoint, params) {
    const handler = modules[endpoint];
    if (!handler) throw new Error('Unsupported endpoint.');
    if (endpoint === 'song/url/v1' || endpoint === 'register/anonimous') await ensureXeapiKey();
    const cookie = cookieToJson(params.cookie || '');
    try {
      return await handler(providerParameters(params, cookie), request);
    } finally {
      // Guest registration rotates upstream global.deviceId. Persist it even if the request fails
      // so the next process/key bootstrap uses the same identity rather than a stale device file.
      if (endpoint === 'register/anonimous') fs.writeFileSync(devicePath, global.deviceId, { mode: 0o600 });
    }
  };
};
