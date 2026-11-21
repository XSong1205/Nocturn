// Explicit routes are also the only provider modules included in the mobile bundle.
// Request transport controls are intentionally absent from the parameter lists.
export const endpoints = Object.freeze({
  'login/qr/key': { module: 'login_qr_key', params: [] },
  'login/qr/create': { module: 'login_qr_create', params: ['key', 'qrimg'], required: ['key'] },
  'login/qr/check': { module: 'login_qr_check', params: ['key'], required: ['key'] },
  'login/status': { module: 'login_status', params: [] },
  'login/refresh': { module: 'login_refresh', params: [] },
  'register/anonimous': { module: 'register_anonimous', params: [], keys: 'xeapi' },
  'captcha/sent': { module: 'captcha_sent', params: ['phone', 'ctcode'], required: ['phone'] },
  'captcha/sent/v1': { module: 'captcha_sent_v1', params: ['phone', 'ctcode'], required: ['phone'] },
  'captcha/verify': { module: 'captcha_verify', params: ['phone', 'captcha', 'ctcode'], required: ['phone', 'captcha'] },
  'login/cellphone': { module: 'login_cellphone', params: ['phone', 'captcha', 'password', 'countrycode'], required: ['phone'] },
  logout: { module: 'logout', params: [] },
  search: { module: 'search', params: ['keywords', 'type', 'limit', 'offset'], required: ['keywords'] },
  cloudsearch: { module: 'cloudsearch', params: ['keywords', 'type', 'limit', 'offset'], required: ['keywords'] },
  'search/suggest': { module: 'search_suggest', params: ['keywords', 'type'], required: ['keywords'] },
  'search/default': { module: 'search_default', params: [] },
  'search/hot/detail': { module: 'search_hot_detail', params: [] },
  'song/detail': { module: 'song_detail', params: ['ids'], required: ['ids'] },
  'song/url/v1': { module: 'song_url_v1', params: ['id', 'level', 'immerseType', 'unblock'], required: ['id', 'level'], keys: 'xeapi', batchId: true },
  'check/music': { module: 'check_music', params: ['id', 'br'], required: ['id'] },
  'song/music/detail': { module: 'song_music_detail', params: ['id'], required: ['id'] },
  lyric: { module: 'lyric', params: ['id'], required: ['id'] },
  'lyric/new': { module: 'lyric_new', params: ['id'], required: ['id'] },
  personalized: { module: 'personalized', params: ['limit'] },
  'personalized/newsong': { module: 'personalized_newsong', params: ['limit'] },
  'recommend/resource': { module: 'recommend_resource', params: [], required: ['cookie'] },
  'recommend/songs': { module: 'recommend_songs', params: ['afresh'] },
  'homepage/block/page': { module: 'homepage_block_page', params: ['refresh', 'cursor'] },
  banner: { module: 'banner', params: ['type'] },
  toplist: { module: 'toplist', params: [] },
  'toplist/detail': { module: 'toplist_detail', params: [] },
  'playlist/catlist': { module: 'playlist_catlist', params: [] },
  'playlist/hot': { module: 'playlist_hot', params: [] },
  'playlist/highquality/tags': { module: 'playlist_highquality_tags', params: [] },
  'top/playlist': { module: 'top_playlist', params: ['cat', 'order', 'limit', 'offset'] },
  'top/playlist/highquality': { module: 'top_playlist_highquality', params: ['cat', 'limit', 'before'] },
  'playlist/detail': { module: 'playlist_detail', params: ['id', 's'], required: ['id'] },
  'playlist/track/all': { module: 'playlist_track_all', params: ['id', 'limit', 'offset'], required: ['id'] },
  'playlist/subscribe': { module: 'playlist_subscribe', params: ['id', 't'], required: ['id', 't'] },
  'playlist/create': { module: 'playlist_create', params: ['name', 'privacy', 'type'], required: ['name', 'cookie'] },
  'playlist/delete': { module: 'playlist_delete', params: ['id'], required: ['id', 'cookie'], batchId: true },
  'playlist/tracks': { module: 'playlist_tracks', params: ['pid', 'tracks', 'op'], required: ['pid', 'tracks', 'op', 'cookie'] },
  'playlist/name/update': { module: 'playlist_name_update', params: ['id', 'name'], required: ['id', 'name', 'cookie'] },
  'playlist/desc/update': { module: 'playlist_desc_update', params: ['id', 'desc'], required: ['id', 'desc', 'cookie'], allowEmpty: ['desc'] },
  'playlist/tags/update': { module: 'playlist_tags_update', params: ['id', 'tags'], required: ['id', 'tags', 'cookie'], allowEmpty: ['tags'] },
  'user/detail': { module: 'user_detail', params: ['uid'], required: ['uid'] },
  'user/account': { module: 'user_account', params: [] },
  'user/subcount': { module: 'user_subcount', params: [] },
  'user/level': { module: 'user_level', params: [] },
  'listen/data/total': { module: 'listen_data_total', params: [] },
  'playmode/intelligence/list': { module: 'playmode_intelligence_list', params: ['id', 'pid', 'sid', 'count'], required: ['id', 'pid'] },
  'user/playlist': { module: 'user_playlist', params: ['uid', 'limit', 'offset'], required: ['uid'] },
  'user/playlist/create': { module: 'user_playlist_create', params: ['uid', 'limit', 'offset'], required: ['uid'] },
  'user/playlist/collect': { module: 'user_playlist_collect', params: ['uid', 'limit', 'offset'], required: ['uid'] },
  'record/recent/song': { module: 'record_recent_song', params: ['limit'] },
  likelist: { module: 'likelist', params: ['uid'], required: ['uid'] },
  like: { module: 'like', params: ['id', 'like'], required: ['id'] },
  'song/like': { module: 'song_like', params: ['id', 'uid', 'like'], required: ['id', 'uid', 'like'] },
  album: { module: 'album', params: ['id'], required: ['id'] },
  'album/sub': { module: 'album_sub', params: ['id', 't'], required: ['id', 't', 'cookie'] },
  'album/sublist': { module: 'album_sublist', params: ['limit', 'offset'], required: ['cookie'] },
  artists: { module: 'artists', params: ['id'], required: ['id'] },
  'artist/detail': { module: 'artist_detail', params: ['id'], required: ['id'] },
  'artist/album': { module: 'artist_album', params: ['id', 'limit', 'offset'], required: ['id'] },
  'artist/songs': { module: 'artist_songs', params: ['id', 'order', 'limit', 'offset'], required: ['id'] },
  'artist/sub': { module: 'artist_sub', params: ['id', 't'], required: ['id', 't', 'cookie'] },
  'artist/sublist': { module: 'artist_sublist', params: ['limit', 'offset'], required: ['cookie'] },
  'personal/fm': { module: 'personal_fm', params: [] },
  'ad/listening/rights': { module: 'ad_listening_rights', params: [], keys: 'xeapi' },
  'ad/listening/rights/gain': { module: 'ad_listening_rights_gain', params: ['reqUid', 'uid', 'rightsGainMethod', 'type_ids', 'creativeType', 'exposureTime', 'clickTime'], keys: 'xeapi' },
  'ad/get': { module: 'ad_get', params: ['type_ids'], keys: 'xeapi' },
  'register/checktoken/v3': { module: 'register_checktoken_v3', params: ['refresh'] },
  'scrobble/v1': { module: 'scrobble_v1', params: ['id', 'time', 'total', 'sourceid', 'source', 'name', 'artist', 'bitrate', 'level', 'vip'], required: ['id', 'time'] },
  scrobble: { module: 'scrobble', params: ['id', 'sourceid', 'time'], required: ['id'] },
  'relay/play/state/submit': { module: 'relay_play_state_submit', params: ['id', 'sessionId', 'progress', 'playMode', 'type'], required: ['id'] },
  'top/song': { module: 'top_song', params: ['type'] },
});

export const audioQualities = Object.freeze([
  'standard', 'higher', 'exhigh', 'lossless', 'hires',
  'jyeffect', 'dolby', 'vivid', 'jymaster', 'sky'
]);
export const immerseTypes = Object.freeze(['c512', 'ste2', 'aac2', 'c51', 'ste', 'aac']);
const levels = new Set(audioQualities);
const immerseTypeSet = new Set(immerseTypes);
const idFields = new Set(['id', 'uid', 'pid', 'sid']);
const countFields = new Set(['limit', 'offset', 's', 'count']);
const positiveId = /^[1-9]\d{0,19}$/;
const searchTypes = new Set(['1', '10', '100', '1000', '1002', '1004', '1006', '1009', '1014', '1018', '2000']);

function validIds(value, batch = false) {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value > 0;
  if (typeof value !== 'string') return false;
  const ids = value.split(',');
  return ids.length <= (batch ? 200 : 1) && ids.every((id) => positiveId.test(id));
}

export function validateParameters(endpoint, body) {
  const spec = Object.hasOwn(endpoints, endpoint) ? endpoints[endpoint] : null;
  if (!spec) return 'Unknown endpoint.';
  if (!body || Array.isArray(body) || typeof body !== 'object') return 'Expected a JSON object.';
  const allowed = new Set([...spec.params, 'cookie']);
  for (const [key, value] of Object.entries(body)) {
    if (!allowed.has(key)) return `Unsupported parameter: ${key}.`;
    if (!['string', 'number', 'boolean'].includes(typeof value)) return `Invalid parameter: ${key}.`;
    if (typeof value === 'number' && !Number.isFinite(value)) return `Invalid parameter: ${key}.`;
    const maxLength = key === 'cookie' ? 16384 : ['ids', 'tracks'].includes(key) || (key === 'id' && spec.batchId) ? 4200 : 4096;
    if (typeof value === 'string' && value.length > maxLength) return `Parameter too long: ${key}.`;
    if (key === 'cookie' && typeof value !== 'string') return 'cookie must be a string.';
    if (key === 'cookie' && /[\r\n\0]/.test(value)) return 'Invalid cookie.';
    if (key === 'cookie' && spec.required?.includes('cookie') && !value.trim()) return 'An account cookie is required.';
    if (idFields.has(key) && !validIds(value, key === 'id' && spec.batchId)) return `Invalid parameter: ${key}.`;
    if (key === 'ids' && (typeof value !== 'string' || !validIds(value, true))) return 'ids must be a string containing at most 200 comma-separated positive IDs.';
    if (key === 'tracks' && (typeof value !== 'string' || !validIds(value, true))) return 'tracks must contain at most 200 comma-separated positive IDs.';
    if (countFields.has(key) && (!/^\d+$/.test(String(value)) || Number(value) > (key === 'offset' ? 100000 : 200))) return `Invalid parameter: ${key}.`;
    if (key === 'limit' && Number(value) < 1) return 'limit must be positive.';
    if (key === 'keywords' && (typeof value !== 'string' || value.trim().length < 1 || value.length > 200)) return 'keywords must contain 1–200 characters.';
    if (key === 'key' && (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,256}$/.test(value))) return 'Invalid QR key.';
    if (key === 'level' && !levels.has(value)) return 'Unsupported audio quality.';
    if (key === 'immerseType' && !immerseTypeSet.has(value)) return 'Unsupported immerse type.';
    if (key === 'unblock' && ![true, false, 'true', 'false'].includes(value)) return 'unblock must be boolean.';
    if (key === 't' && !['1', '2'].includes(String(value))) return 't must be 1 or 2.';
    if (['name', 'cat'].includes(key)) {
      const maxLen = endpoint === 'scrobble/v1' ? 200 : 40;
      if (typeof value !== 'string' || !value.trim() || value.length > maxLen) return `${key} must contain 1–${maxLen} characters.`;
    }
    if (key === 'desc' && (typeof value !== 'string' || value.length > 1000)) return 'desc must contain at most 1000 characters.';
    if (key === 'tags' && (typeof value !== 'string' || (value !== '' && (value.split(';').length > 3 || value.split(';').some((tag) => !tag.trim() || tag.length > 40))))) return 'tags must be empty or contain 1–3 labels of at most 40 characters.';
    if (key === 'privacy' && !['0', '10'].includes(String(value))) return 'privacy must be 0 or 10.';
    if (key === 'op' && !['add', 'del'].includes(value)) return 'op must be add or del.';
    if (key === 'order' && !(endpoint === 'artist/songs' ? ['hot', 'time'] : ['hot', 'new']).includes(value)) return 'Unsupported order.';
    if (key === 'before' && (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value)))) return 'before must be a nonnegative safe integer timestamp.';
    if (key === 'like' && ![true, false, 'true', 'false'].includes(value)) return 'like must be boolean.';
    if (['qrimg', 'afresh', 'refresh'].includes(key) && typeof value !== 'boolean') return `${key} must be boolean.`;
    if (key === 'type') {
      const accepted = endpoint === 'search/suggest' ? ['mobile', 'web'].includes(value)
        : endpoint === 'banner' ? ['0', '1', '2', '3'].includes(String(value))
        : endpoint === 'playlist/create' ? ['NORMAL', 'VIDEO', 'SHARED'].includes(value)
        : endpoint === 'relay/play/state/submit' ? ['song', 'video', 'program'].includes(value)
        : endpoint === 'top/song' ? ['0', '7', '96', '8', '16'].includes(String(value))
        : searchTypes.has(String(value));
      if (!accepted) return 'Unsupported type.';
    }
    if (key === 'sourceid' && (typeof value !== 'string' && typeof value !== 'number' || (String(value) !== '' && !validIds(value)))) return 'Invalid sourceid.';
    if (['time', 'total', 'progress'].includes(key)) {
      const num = Number(value);
      if (!Number.isFinite(num) || num < 0 || num > 864000) return `Invalid parameter: ${key}.`;
      if (key === 'time' && endpoint === 'scrobble/v1' && num <= 0) return 'time must be positive.';
    }
    if (key === 'bitrate' && (!Number.isSafeInteger(Number(value)) || Number(value) < 1 || Number(value) > 10000000)) return 'Invalid bitrate.';
    if (key === 'vip' && ![true, false, 'true', 'false'].includes(value)) return 'vip must be boolean.';
    if (key === 'source' && (typeof value !== 'string' || value.length > 50)) return 'Invalid source.';
    if (key === 'artist' && (typeof value !== 'string' || value.length > 200)) return 'artist must contain at most 200 characters.';
    if (key === 'sessionId' && (typeof value !== 'string' || !/^[A-Za-z0-9]{1,32}$/.test(value))) return 'Invalid sessionId.';
    if (key === 'playMode' && (typeof value !== 'string' || value.length > 32)) return 'Invalid playMode.';
    if (key === 'phone' && (typeof value !== 'string' || !/^\d{5,15}$/.test(value))) return 'Invalid phone number.';
    if (['ctcode', 'countrycode'].includes(key) && (typeof value !== 'string' || !/^[1-9]\d{0,3}$/.test(value))) return 'Invalid country code.';
    if (key === 'captcha' && (typeof value !== 'string' || !/^\d{4,8}$/.test(value))) return 'Invalid verification code.';
    if (key === 'password' && (typeof value !== 'string' || value.length < 1 || value.length > 128)) return 'Invalid password.';
    if (key === 'cursor' && (typeof value !== 'string' || value.length > 4096)) return 'Invalid cursor.';
    if (key === 'br' && (!['string', 'number'].includes(typeof value) || !Number.isSafeInteger(Number(value)) || Number(value) < 1 || Number(value) > 10000000)) return 'Invalid bitrate.';
    if (key === 'type_ids' && (typeof value !== 'string' || value.length > 200)) return 'Invalid type_ids.';
    if (key === 'reqUid' && (typeof value !== 'string' || value.length > 256)) return 'Invalid reqUid.';
    if (key === 'rightsGainMethod' && (!/^\d+$/.test(String(value)) || Number(value) < 1 || Number(value) > 10)) return 'Invalid rightsGainMethod.';
    if (key === 'creativeType' && (!/^\d+$/.test(String(value)) || Number(value) < 0 || Number(value) > 1000)) return 'Invalid creativeType.';
    if (['exposureTime', 'clickTime'].includes(key) && (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value)))) return `${key} must be a nonnegative safe integer timestamp.`;
  }
  for (const required of spec.required || []) {
    if (!Object.hasOwn(body, required) || (body[required] === '' && !spec.allowEmpty?.includes(required))) return `Missing parameter: ${required}.`;
  }
  if (endpoint === 'login/cellphone' && Object.hasOwn(body, 'captcha') === Object.hasOwn(body, 'password')) {
    return 'Supply exactly one of captcha or password.';
  }
  return null;
}
